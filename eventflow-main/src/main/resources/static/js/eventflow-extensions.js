/* EventFlow extensions: authentication, role-based UI, registration/waitlist, QR tickets, check-in,
   certificates, user admin and analytics insights. Loaded after the main script in index.html and
   exposed as window.EF. All permission checks here only hide buttons: the server enforces every rule. */
(function () {
    'use strict';

    const EF = { user: null, myRegs: null, ci: { eventId: null, stream: null, scanning: false, busy: false, lastCode: null, lastTime: 0, detector: null, recent: [] }, charts: {} };
    window.EF = EF;
    const $ = (id) => document.getElementById(id);
    const OPEN = ['Upcoming', 'In Progress'];
    const PAGE_ROLES = { create: ['ORGANIZER', 'ADMIN'], analytics: ['ORGANIZER', 'ADMIN'], checkin: ['ORGANIZER', 'ADMIN'], users: ['ADMIN'] };

    // ------------------------------------------------------------------ helpers
    EF.esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    const esc = EF.esc;
    const fmtDateTime = (iso) => (iso ? new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '-');

    function csrfToken() {
        const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        return m ? decodeURIComponent(m[1]) : '';
    }

    // Adds the CSRF header to every state-changing same-origin request (so the original fetch calls keep
    // working unchanged) and returns to the sign-in screen when the session has expired.
    const nativeFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
        init = init || {};
        const url = typeof input === 'string' ? input : input.url;
        const method = String(init.method || 'GET').toUpperCase();
        const sameOrigin = url.startsWith('/') || url.startsWith(location.origin);
        if (sameOrigin && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
            const headers = new Headers(init.headers || {});
            const token = csrfToken();
            if (token) headers.set('X-XSRF-TOKEN', token);
            init = Object.assign({}, init, { headers });
        }
        return nativeFetch(input, init).then((res) => {
            if (res.status === 401 && sameOrigin && !url.includes('/api/auth/')) {
                EF.showLogin('Your session has expired. Please sign in again.');
            }
            return res;
        });
    };

    EF.api = async function (url, options) {
        const opts = Object.assign({ method: 'GET' }, options || {});
        opts.headers = Object.assign({ Accept: 'application/json' }, opts.headers || {});
        if (opts.body !== undefined && !(opts.body instanceof FormData)) {
            opts.headers['Content-Type'] = 'application/json';
            opts.body = JSON.stringify(opts.body);
        }
        let res;
        try {
            res = await fetch(url, opts);
        } catch (e) {
            throw new Error('Cannot reach the server. Check your connection and try again.');
        }
        let data = null;
        if ((res.headers.get('content-type') || '').includes('application/json')) {
            data = await res.json().catch(() => null);
        }
        if (!res.ok) {
            const err = new Error((data && data.error) || 'Request failed (' + res.status + ')');
            err.status = res.status;
            err.data = data;
            throw err;
        }
        return data;
    };

    EF.toast = function (message, type) {
        const host = $('efToasts');
        if (!host) return;
        const el = document.createElement('div');
        el.className = 'ef-toast ' + (type || 'info');
        el.textContent = message;
        host.appendChild(el);
        setTimeout(() => el.remove(), type === 'error' ? 7000 : 4500);
        if (typeof addNotification === 'function') addNotification((type === 'error' ? '❌ ' : '✅ ') + message, type || 'info', 5000);
    };

    const setLoading = (el, text) => { el.innerHTML = '<div class="ef-state"><div class="ef-spin"></div>' + esc(text || 'Loading...') + '</div>'; };
    const setError = (el, msg, retry) => {
        el.innerHTML = '<div class="ef-state error"><i class="fas fa-triangle-exclamation"></i><h3>Something went wrong</h3><p>' + esc(msg) + '</p>' +
            (retry ? '<button class="btn btn-outline btn-sm" onclick="' + retry + '">Try again</button>' : '') + '</div>';
    };
    const setEmpty = (el, icon, title, text, actionHtml) => {
        el.innerHTML = '<div class="ef-state"><i class="fas ' + icon + '"></i><h3>' + esc(title) + '</h3><p>' + esc(text) + '</p>' + (actionHtml || '') + '</div>';
    };

    // ------------------------------------------------------------------ roles
    EF.role = () => (EF.user ? EF.user.role : null);
    EF.isParticipant = () => EF.role() === 'PARTICIPANT';
    EF.isManager = () => EF.role() === 'ORGANIZER' || EF.role() === 'ADMIN';
    EF.canManage = (event) => EF.role() === 'ADMIN' || (EF.role() === 'ORGANIZER' && event && event.organizerId === EF.user.id);
    EF.canOpen = (page) => !PAGE_ROLES[page] || PAGE_ROLES[page].includes(EF.role());

    function applyRole() {
        document.querySelectorAll('[data-roles]').forEach((el) => {
            el.classList.toggle('hidden', !el.dataset.roles.split(',').includes(EF.role()));
        });
        $('efUserName').textContent = EF.user.name;
        $('efUserRole').textContent = EF.user.role.toLowerCase();
        $('efUserAvatar').textContent = EF.user.name.charAt(0).toUpperCase();
    }

    // ------------------------------------------------------------------ sign in / out
    EF.showLogin = function (message) {
        EF.user = null;
        EF.stopCamera();
        $('authScreen').classList.remove('hidden');
        $('authError').textContent = message || '';
    };

    EF.boot = async function () {
        try {
            EF.user = await EF.api('/api/auth/me');
        } catch (e) {
            EF.user = null;
        }
        if (!EF.user) {
            EF.showLogin();
            return false;
        }
        onLoggedIn();
        return true;
    };

    function onLoggedIn() {
        $('authScreen').classList.add('hidden');
        applyRole();
        if (typeof useMockData !== 'undefined') useMockData = false; // never show mock data to a signed-in user
    }

    EF.logout = async function () {
        try { await EF.api('/api/auth/logout', { method: 'POST' }); } catch (e) { /* ignore */ }
        location.reload();
    };

    async function submitAuth(mode, form) {
        const err = $('authError');
        err.textContent = '';
        const btn = form.querySelector('button[type=submit]');
        btn.disabled = true;
        try {
            const body = mode === 'login'
                ? { email: form.email.value, password: form.password.value }
                : { name: form.name.value, email: form.email.value, password: form.password.value };
            EF.user = await EF.api(mode === 'login' ? '/api/auth/login' : '/api/auth/register', { method: 'POST', body });
            form.reset();
            onLoggedIn();
            if (typeof fetchAllEvents === 'function') await fetchAllEvents();
            showPage('dashboard');
        } catch (e) {
            err.textContent = e.message;
        } finally {
            btn.disabled = false;
        }
    }

    EF.authTab = function (mode) {
        $('loginForm').classList.toggle('hidden', mode !== 'login');
        $('signupForm').classList.toggle('hidden', mode !== 'signup');
        $('tabLogin').classList.toggle('active', mode === 'login');
        $('tabSignup').classList.toggle('active', mode === 'signup');
        $('authError').textContent = '';
    };

    EF.init = function () {
        $('loginForm').addEventListener('submit', (e) => { e.preventDefault(); submitAuth('login', e.target); });
        $('signupForm').addEventListener('submit', (e) => { e.preventDefault(); submitAuth('signup', e.target); });
    };

    // called by showPage() in index.html
    EF.onPageShown = function (page) {
        if (page !== 'checkin') EF.stopCamera();
        if (page === 'tickets') EF.loadTickets();
        if (page === 'checkin') EF.loadCheckin();
        if (page === 'certificates') EF.loadCertificates();
        if (page === 'users') EF.loadUsers();
        if (page === 'analytics') EF.loadInsights();
    };

    // ------------------------------------------------------------------ badges and seat info
    EF.regBadge = function (r) {
        if (r.status === 'CONFIRMED') return r.checkedIn ? '<span class="badge badge-success">Checked in</span>' : '<span class="badge badge-primary">Confirmed</span>';
        if (r.status === 'WAITLISTED') return '<span class="badge badge-warning">Waitlisted #' + r.waitlistPosition + '</span>';
        return '<span class="badge badge-danger">Cancelled</span>';
    };

    EF.seatsCell = function (e) {
        if (!e.capacity) return '<span class="ef-muted">Not set</span>';
        const wait = e.waitlistedCount ? '<div class="ef-muted" style="font-size:.75rem">' + e.waitlistedCount + ' waiting</div>' : '';
        return '<strong>' + (e.confirmedCount || 0) + ' / ' + e.capacity + '</strong>' + wait;
    };

    function meter(e) {
        if (!e.capacity) return '';
        const pct = Math.min(100, Math.round(((e.confirmedCount || 0) * 100) / e.capacity));
        return '<div class="ef-meter ' + (pct >= 100 ? 'full' : '') + '"><span style="width:' + pct + '%"></span></div>' +
            '<div class="ef-seat-line"><span>' + (e.confirmedCount || 0) + ' of ' + e.capacity + ' seats taken</span><span>' +
            (e.availableSeats > 0 ? e.availableSeats + ' left' : 'Full' + (e.waitlistedCount ? ' · ' + e.waitlistedCount + ' waiting' : '')) + '</span></div>';
    }

    EF.posterHtml = function (e, cls) {
        return e.posterUrl ? '<img class="' + cls + '" src="' + esc(e.posterUrl) + '" alt="Poster for ' + esc(e.name) + '" loading="lazy">' : '';
    };

    // ------------------------------------------------------------------ registration (event dialog)
    EF.myRegistrations = async function (force) {
        if (!EF.myRegs || force) EF.myRegs = await EF.api('/api/registrations/mine');
        return EF.myRegs;
    };

    EF.renderRegistrationPanel = async function (event) {
        const host = $('eventRegPanel');
        if (!host) return;
        if (EF.isManager()) {
            host.innerHTML = '<div class="ef-panel"><h4>Registrations</h4>' + (meter(event) || '<p>No capacity set for this event.</p>') +
                (EF.canManage(event) ? '<div class="ef-actions"><button class="btn btn-outline btn-sm" onclick="EF.openCheckin(' + event.id + ')"><i class="fas fa-qrcode"></i> Check-in</button>' +
                    '<button class="btn btn-outline btn-sm" onclick="EF.openCertificates(' + event.id + ')"><i class="fas fa-certificate"></i> Certificates</button></div>' : '<p>Only the organizer of this event can manage it.</p>') + '</div>';
            return;
        }
        setLoading(host, 'Checking your registration...');
        let regs;
        try { regs = await EF.myRegistrations(true); } catch (e) { setError(host, e.message, 'EF.renderRegistrationPanel(allEvents.find(x=>x.id===' + event.id + '))'); return; }
        const mine = regs.find((r) => r.eventId === event.id && r.status !== 'CANCELLED');
        const open = OPEN.includes(event.status);
        let body;
        if (mine && mine.status === 'CONFIRMED') {
            body = '<p>You are registered' + (mine.checkedIn ? ' and checked in.' : '. Show your QR ticket at the entrance.') + '</p>' + meter(event) +
                '<div class="ef-actions"><button class="btn btn-primary btn-sm" onclick="closeModal(\'eventDetailModal\'); showPage(\'tickets\')"><i class="fas fa-ticket"></i> View ticket</button>' +
                (mine.checkedIn ? '' : '<button class="btn ef-btn-danger btn-sm" onclick="EF.cancel(' + mine.id + ',' + event.id + ')">Cancel registration</button>') + '</div>';
        } else if (mine) {
            body = '<p>The event is full. You are <strong>#' + mine.waitlistPosition + '</strong> on the waitlist and will get a ticket automatically if a seat opens up.</p>' +
                '<div class="ef-actions"><button class="btn ef-btn-danger btn-sm" onclick="EF.cancel(' + mine.id + ',' + event.id + ')">Leave waitlist</button></div>';
        } else if (!open) {
            body = '<p>Registration is closed for this event.</p>';
        } else if (!event.capacity) {
            body = '<p>Registration is not open yet: the organizer has not set a capacity.</p>';
        } else if (event.availableSeats > 0) {
            body = meter(event) + '<div class="ef-actions"><button class="btn btn-primary" onclick="EF.register(' + event.id + ')"><i class="fas fa-ticket"></i> Register now</button></div>';
        } else {
            body = '<p>This event is full. Join the waitlist and you will be promoted automatically if someone cancels.</p>' + meter(event) +
                '<div class="ef-actions"><button class="btn btn-primary" onclick="EF.register(' + event.id + ')"><i class="fas fa-hourglass-half"></i> Join waitlist</button></div>';
        }
        host.innerHTML = '<div class="ef-panel"><h4>Your registration</h4>' + body + '</div>';
    };

    async function refreshAfterChange(eventId) {
        EF.myRegs = null;
        if (typeof fetchAllEvents === 'function') await fetchAllEvents();
        if ($('eventDetailModal').classList.contains('active') && eventId) viewEvent(eventId);
        const active = document.querySelector('.page.active');
        if (active) EF.onPageShown(active.id);
        if (active && (active.id === 'dashboard' || active.id === 'events') && typeof showPage === 'function') showPage(active.id);
    }

    EF.register = async function (eventId) {
        try {
            const r = await EF.api('/api/events/' + eventId + '/register', { method: 'POST' });
            EF.toast(r.status === 'CONFIRMED' ? 'You are registered. Your ticket is in My Tickets.' : 'The event is full: you are #' + r.waitlistPosition + ' on the waitlist.', 'success');
        } catch (e) {
            EF.toast(e.message, 'error');
        }
        await refreshAfterChange(eventId);
    };

    EF.cancel = async function (registrationId, eventId) {
        if (!confirm('Cancel this registration? Your ticket will stop working.')) return;
        try {
            await EF.api('/api/registrations/' + registrationId + '/cancel', { method: 'POST' });
            EF.toast('Registration cancelled.', 'success');
        } catch (e) {
            EF.toast(e.message, 'error');
        }
        await refreshAfterChange(eventId);
    };

    // ------------------------------------------------------------------ posters
    EF.uploadPoster = async function (eventId, file) {
        if (!file || !file.size) return;
        if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) throw new Error('Poster must be a PNG, JPEG or WebP image');
        if (file.size > 2 * 1024 * 1024) throw new Error('Poster must be 2 MB or smaller');
        const fd = new FormData();
        fd.append('file', file);
        await EF.api('/api/events/' + eventId + '/poster', { method: 'POST', body: fd });
    };

    EF.removePoster = async function (eventId) {
        try {
            await EF.api('/api/events/' + eventId + '/poster', { method: 'DELETE' });
            EF.toast('Poster removed.', 'success');
            await fetchAllEvents();
            closeModal('editEventModal');
            loadAllEvents();
        } catch (e) { EF.toast(e.message, 'error'); }
    };

    // ------------------------------------------------------------------ dashboards
    EF.loadParticipantDashboard = async function () {
        ['dashboardStats', 'dashboardRegStats', 'orgUpcomingCard'].forEach((id) => $(id).classList.add('hidden'));
        const host = $('participantDashboard');
        host.classList.remove('hidden');
        setLoading(host);
        try {
            const [events, regs] = await Promise.all([fetchAllEvents(), EF.myRegistrations(true)]);
            const certs = regs.filter((r) => r.certificateId).length;
            const stat = (v, label, icon, cls) => '<div class="stat-card"><div class="stat-header"><div><div class="stat-value">' + v + '</div><div class="stat-label">' + label +
                '</div></div><div class="stat-icon ' + cls + '"><i class="fas ' + icon + '"></i></div></div></div>';
            const open = events.filter((e) => OPEN.includes(e.status)).sort((a, b) => new Date(a.eventDate) - new Date(b.eventDate)).slice(0, 6);
            const regFor = (id) => regs.find((r) => r.eventId === id && r.status !== 'CANCELLED');
            host.innerHTML = '<div class="stats-grid">' +
                stat(regs.filter((r) => r.status === 'CONFIRMED').length, 'Confirmed tickets', 'fa-ticket', 'primary') +
                stat(regs.filter((r) => r.status === 'WAITLISTED').length, 'On waitlist', 'fa-hourglass-half', 'warning') +
                stat(regs.filter((r) => r.checkedIn).length, 'Events attended', 'fa-check-circle', 'success') +
                stat(certs, 'Certificates', 'fa-certificate', 'primary') + '</div>' +
                '<div class="card"><div class="card-header"><h3 class="card-title">Open for registration</h3><button class="btn btn-outline btn-sm" onclick="showPage(\'events\')">All events</button></div>' +
                (open.length ? '<div class="ef-event-grid">' + open.map((e) => {
                    const r = regFor(e.id);
                    const status = r ? EF.regBadge(r) : (e.availableSeats > 0 ? '<span class="badge badge-success">' + e.availableSeats + ' seats left</span>' : (e.capacity ? '<span class="badge badge-warning">Full · waitlist</span>' : '<span class="badge badge-primary">Not open</span>'));
                    return '<article class="ef-event-card">' + (e.posterUrl ? EF.posterHtml(e, 'ef-poster') : '<div class="ef-poster-empty"><i class="fas fa-calendar-days"></i></div>') +
                        '<div class="ef-event-body"><div class="ef-event-title">' + esc(e.name) + '</div><div class="ef-event-meta"><span><i class="fas fa-calendar"></i> ' + formatDate(e.eventDate) + (e.eventTime ? ' · ' + esc(e.eventTime) : '') +
                        '</span><span><i class="fas fa-location-dot"></i> ' + esc(e.location) + '</span></div><div class="ef-event-foot">' + status +
                        '<button class="btn btn-outline btn-sm" onclick="viewEvent(' + e.id + ')">Details</button></div></div></article>';
                }).join('') + '</div>' : '<div class="ef-state"><i class="fas fa-calendar-xmark"></i><h3>No open events</h3><p>New events will appear here when organizers publish them.</p></div>') + '</div>';
        } catch (e) {
            setError(host, e.message, 'EF.loadParticipantDashboard()');
        }
    };

    EF.afterDashboard = async function () {
        $('participantDashboard').classList.add('hidden');
        ['dashboardStats', 'dashboardRegStats', 'orgUpcomingCard'].forEach((id) => $(id).classList.remove('hidden'));
        const host = $('dashboardRegStats');
        try {
            const o = await EF.api('/api/analytics/overview');
            host.innerHTML = [
                [o.totalRegistrations, 'Registrations', 'fa-user-plus', 'primary'], [o.confirmedRegistrations, 'Confirmed', 'fa-circle-check', 'success'],
                [o.waitlistedRegistrations, 'Waitlisted', 'fa-hourglass-half', 'warning'], [o.totalAttendees, 'Attended', 'fa-qrcode', 'success'],
                [o.attendancePercent + '%', 'Attendance rate', 'fa-percent', 'primary'], [o.availableSeats, 'Seats available', 'fa-chair', 'warning'],
            ].map((s) => '<div class="stat-card"><div class="stat-header"><div><div class="stat-value">' + s[0] + '</div><div class="stat-label">' + s[1] +
                '</div></div><div class="stat-icon ' + s[3] + '"><i class="fas ' + s[2] + '"></i></div></div></div>').join('');
        } catch (e) {
            setError(host, e.message, 'EF.afterDashboard()');
        }
    };

    // ------------------------------------------------------------------ tickets page
    EF.loadTickets = async function () {
        const host = $('ticketsContent');
        setLoading(host);
        try {
            const regs = await EF.myRegistrations(true);
            if (!regs.length) {
                setEmpty(host, 'fa-ticket', 'No tickets yet', 'Register for an event and your QR ticket will appear here.', '<button class="btn btn-primary" onclick="showPage(\'events\')">Browse events</button>');
                return;
            }
            const active = regs.filter((r) => r.status !== 'CANCELLED');
            const past = regs.filter((r) => r.status === 'CANCELLED');
            host.innerHTML = (active.length ? '<div class="ef-ticket-list">' + active.map(ticketCard).join('') + '</div>' : '<div class="ef-state"><p>You have no active registrations.</p></div>') +
                (past.length ? '<h3 class="ef-section-title">Cancelled</h3><div class="ef-ticket-list">' + past.map(ticketCard).join('') + '</div>' : '');
        } catch (e) {
            setError(host, e.message, 'EF.loadTickets()');
        }
    };

    function ticketCard(r) {
        const cls = r.status === 'CANCELLED' ? 'is-cancelled' : (r.status === 'WAITLISTED' ? 'is-waitlisted' : '');
        let side;
        if (r.status === 'CONFIRMED') {
            side = '<img src="/api/registrations/' + r.id + '/qr.png" alt="QR code for ticket ' + esc(r.ticketId) + '" width="150" height="150"><code>' + esc(r.ticketId) + '</code>';
        } else if (r.status === 'WAITLISTED') {
            side = '<div>Waitlist position</div><div class="ef-queue-num">#' + r.waitlistPosition + '</div><div>You get a ticket automatically if a seat opens.</div>';
        } else {
            side = '<i class="fas fa-ban" style="font-size:2rem"></i><div>Ticket no longer valid</div>';
        }
        const actions = [];
        if (r.status === 'CONFIRMED') actions.push('<a class="btn btn-outline btn-sm" href="/api/registrations/' + r.id + '/ticket.pdf" download><i class="fas fa-download"></i> PDF</a>');
        if (r.certificateId) actions.push('<a class="btn btn-primary btn-sm" href="/api/certificates/' + r.certificateId + '/download" download><i class="fas fa-certificate"></i> Certificate</a>');
        if (r.status !== 'CANCELLED' && !r.checkedIn) actions.push('<button class="btn ef-btn-danger btn-sm" onclick="EF.cancel(' + r.id + ',' + r.eventId + ')">' + (r.status === 'WAITLISTED' ? 'Leave waitlist' : 'Cancel') + '</button>');
        return '<article class="ef-ticket ' + cls + '"><div class="ef-ticket-main"><div class="ef-ticket-head"><h3>' + esc(r.eventName) + '</h3>' + EF.regBadge(r) + '</div>' +
            '<dl class="ef-meta"><dt>Participant</dt><dd>' + esc(r.participantName) + '</dd><dt>Date</dt><dd>' + formatDate(r.eventDate) + (r.eventTime ? ' · ' + esc(r.eventTime) : '') + '</dd>' +
            '<dt>Venue</dt><dd>' + esc(r.location) + '</dd>' + (r.ticketId ? '<dt>Ticket ID</dt><dd>' + esc(r.ticketId) + '</dd>' : '') +
            (r.checkedIn ? '<dt>Checked in</dt><dd>' + fmtDateTime(r.checkedInAt) + '</dd>' : '') + '</dl>' +
            '<div class="ef-actions">' + actions.join('') + '</div></div><div class="ef-ticket-qr">' + side + '</div></article>';
    }

    // ------------------------------------------------------------------ check-in page
    function manageableEvents() {
        return (typeof allEvents !== 'undefined' ? allEvents : []).filter((e) => EF.canManage(e));
    }

    function eventOptions(selected) {
        return manageableEvents().map((e) => '<option value="' + e.id + '"' + (e.id === selected ? ' selected' : '') + '>' + esc(e.name) + ' (' + formatDate(e.eventDate) + ')</option>').join('');
    }

    EF.openCheckin = function (eventId) { EF.ci.eventId = eventId; closeModal('eventDetailModal'); showPage('checkin'); };
    EF.openCertificates = function (eventId) { EF.certEventId = eventId; closeModal('eventDetailModal'); showPage('certificates'); };

    EF.loadCheckin = async function () {
        const host = $('checkinContent');
        setLoading(host);
        try { await fetchAllEvents(); } catch (e) { setError(host, e.message, 'EF.loadCheckin()'); return; }
        const events = manageableEvents();
        if (!events.length) {
            setEmpty(host, 'fa-qrcode', 'No events to check in', 'Create an event first; attendance is managed per event.', '<button class="btn btn-primary" onclick="showPage(\'create\')">Create event</button>');
            return;
        }
        if (!events.some((e) => e.id === EF.ci.eventId)) EF.ci.eventId = events[0].id;
        host.innerHTML = '<div class="ef-toolbar"><label for="ciEvent">Event</label><select id="ciEvent" class="form-select">' + eventOptions(EF.ci.eventId) + '</select></div>' +
            '<div class="stats-grid" id="ciStats"></div>' +
            '<div class="ef-two-col"><div class="card"><div class="card-header"><h3 class="card-title">Scan ticket</h3><button class="btn btn-primary btn-sm" id="ciCamBtn"><i class="fas fa-camera"></i> Start camera</button></div>' +
            '<div class="ef-scan-wrap"><video id="ciVideo" playsinline muted></video><div class="ef-scan-hint" id="ciHint">Camera is off. Start the camera or type the ticket ID below.</div></div>' +
            '<form id="ciManualForm" class="ef-manual"><label class="ef-sr-only" for="ciTicket">Ticket ID</label><input id="ciTicket" class="form-input" placeholder="EF-XXXX-XXXX-XXXX" autocomplete="off" maxlength="30" required>' +
            '<button class="btn btn-outline btn-sm" type="submit">Check in</button></form><div id="ciResult" aria-live="polite"></div></div>' +
            '<div class="card"><div class="card-header"><h3 class="card-title">Registrations</h3></div><div class="ef-roster ef-table-wrap" id="ciRoster"></div></div></div>';
        $('ciEvent').addEventListener('change', (e) => { EF.ci.eventId = parseInt(e.target.value, 10); $('ciResult').innerHTML = ''; EF.refreshCheckin(); });
        $('ciCamBtn').addEventListener('click', () => (EF.ci.scanning ? EF.stopCamera() : EF.startCamera()));
        $('ciManualForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const input = $('ciTicket');
            await EF.submitCheckIn(input.value);
            input.value = '';
            input.focus();
        });
        EF.refreshCheckin();
    };

    EF.refreshCheckin = async function () {
        const id = EF.ci.eventId;
        const stats = $('ciStats');
        const roster = $('ciRoster');
        if (!stats || !roster) return;
        setLoading(roster);
        try {
            const [s, regs] = await Promise.all([EF.api('/api/checkin/events/' + id + '/stats'), EF.api('/api/events/' + id + '/registrations')]);
            const card = (v, l, icon, cls) => '<div class="stat-card"><div class="stat-header"><div><div class="stat-value">' + v + '</div><div class="stat-label">' + l + '</div></div><div class="stat-icon ' + cls + '"><i class="fas ' + icon + '"></i></div></div></div>';
            stats.innerHTML = card(s.confirmed, 'Confirmed', 'fa-user-check', 'primary') + card(s.checkedIn, 'Checked in', 'fa-qrcode', 'success') + card(s.remaining, 'Still to arrive', 'fa-hourglass-half', 'warning') + card(s.attendancePercent + '%', 'Attendance', 'fa-percent', 'primary');
            const rows = regs.filter((r) => r.status !== 'CANCELLED');
            roster.innerHTML = rows.length ? '<table><thead><tr><th>Participant</th><th>Status</th><th>Ticket</th><th>Checked in</th></tr></thead><tbody>' +
                rows.map((r) => '<tr><td><strong>' + esc(r.participantName) + '</strong><div class="ef-muted" style="font-size:.75rem">' + esc(r.participantEmail) + '</div></td><td>' + EF.regBadge(r) + '</td><td><code>' + esc(r.ticketId || '-') + '</code></td><td>' + (r.checkedIn ? fmtDateTime(r.checkedInAt) : '-') + '</td></tr>').join('') + '</tbody></table>' :
                '<div class="ef-state"><i class="fas fa-user-slash"></i><h3>No registrations yet</h3><p>Participants will appear here once they register.</p></div>';
        } catch (e) {
            setError(roster, e.message, 'EF.refreshCheckin()');
        }
    };

    EF.submitCheckIn = async function (code) {
        const box = $('ciResult');
        if (!box || !EF.ci.eventId) return;
        let result;
        try {
            result = await EF.api('/api/checkin', { method: 'POST', body: { eventId: EF.ci.eventId, ticketId: String(code).trim() } });
        } catch (e) {
            result = e.data && e.data.code ? e.data : { success: false, code: 'ERROR', message: e.message };
        }
        const ok = result.success;
        box.innerHTML = '<div class="ef-result ' + (ok ? 'ok' : 'bad') + '" role="status"><strong><i class="fas ' + (ok ? 'fa-circle-check' : 'fa-circle-xmark') + '"></i> ' + esc(result.message) + '</strong>' +
            (result.participantName || result.eventName ? '<dl class="ef-meta">' + (result.participantName ? '<dt>Participant</dt><dd>' + esc(result.participantName) + '</dd>' : '') +
                '<dt>Event</dt><dd>' + esc(result.eventName || '') + '</dd>' + (result.eventDate ? '<dt>Date</dt><dd>' + formatDate(result.eventDate) + '</dd>' : '') +
                (result.ticketId ? '<dt>Ticket</dt><dd>' + esc(result.ticketId) + '</dd>' : '') + (result.checkedInAt ? '<dt>Checked in</dt><dd>' + fmtDateTime(result.checkedInAt) + '</dd>' : '') + '</dl>' : '') + '</div>';
        if (ok) EF.refreshCheckin();
    };

    EF.startCamera = async function () {
        const hint = $('ciHint');
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            hint.textContent = 'Camera is not available here (browsers only allow it on HTTPS or localhost). Type the ticket ID instead.';
            return;
        }
        try {
            EF.ci.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
        } catch (e) {
            hint.textContent = 'Camera access was blocked or no camera was found. Type the ticket ID instead.';
            return;
        }
        const video = $('ciVideo');
        video.srcObject = EF.ci.stream;
        await video.play().catch(() => {});
        hint.classList.add('hidden');
        $('ciCamBtn').innerHTML = '<i class="fas fa-stop"></i> Stop camera';
        EF.ci.detector = 'BarcodeDetector' in window ? new BarcodeDetector({ formats: ['qr_code'] }) : null;
        EF.ci.scanning = true;
        scanLoop();
    };

    EF.stopCamera = function () {
        EF.ci.scanning = false;
        if (EF.ci.stream) { EF.ci.stream.getTracks().forEach((t) => t.stop()); EF.ci.stream = null; }
        const btn = $('ciCamBtn'), hint = $('ciHint'), video = $('ciVideo');
        if (video) video.srcObject = null;
        if (btn) btn.innerHTML = '<i class="fas fa-camera"></i> Start camera';
        if (hint) { hint.classList.remove('hidden'); hint.textContent = 'Camera is off. Start the camera or type the ticket ID below.'; }
    };

    async function scanLoop() {
        if (!EF.ci.scanning) return;
        const video = $('ciVideo');
        if (video && video.readyState >= 2 && !EF.ci.busy) {
            let code = null;
            try {
                if (EF.ci.detector) {
                    const found = await EF.ci.detector.detect(video);
                    if (found.length) code = found[0].rawValue;
                } else if (window.jsQR) {
                    const scale = Math.min(1, 480 / video.videoWidth);
                    const w = Math.max(1, Math.round(video.videoWidth * scale)), h = Math.max(1, Math.round(video.videoHeight * scale));
                    const canvas = EF.ci.canvas || (EF.ci.canvas = document.createElement('canvas'));
                    canvas.width = w; canvas.height = h;
                    const ctx = canvas.getContext('2d', { willReadFrequently: true });
                    ctx.drawImage(video, 0, 0, w, h);
                    const hit = window.jsQR(ctx.getImageData(0, 0, w, h).data, w, h, { inversionAttempts: 'dontInvert' });
                    if (hit) code = hit.data;
                }
            } catch (e) { /* ignore a bad frame */ }
            if (code && !(code === EF.ci.lastCode && Date.now() - EF.ci.lastTime < 4000)) {
                EF.ci.lastCode = code; EF.ci.lastTime = Date.now(); EF.ci.busy = true;
                await EF.submitCheckIn(code);
                EF.ci.busy = false;
            }
        }
        setTimeout(scanLoop, 250);
    }

    // ------------------------------------------------------------------ certificates page
    EF.loadCertificates = async function () {
        const host = $('certificatesContent');
        setLoading(host);
        try {
            const mine = await EF.api('/api/certificates/mine');
            let html = '<h3 class="ef-section-title">My certificates</h3>' + (mine.length ? '<div class="ef-cert-list">' + mine.map((c) =>
                '<article class="ef-cert"><h3>' + esc(c.eventName) + '</h3><div class="ef-muted" style="font-size:.85rem">' + formatDate(c.eventDate) + ' · ' + esc(c.organizerName || '') + '</div><code>' + esc(c.certificateCode) + '</code>' +
                '<div class="ef-actions"><a class="btn btn-primary btn-sm" href="/api/certificates/' + c.id + '/download" download><i class="fas fa-download"></i> Download PDF</a></div></article>').join('') + '</div>'
                : '<div class="ef-state"><i class="fas fa-certificate"></i><h3>No certificates yet</h3><p>Certificates are issued automatically after you check in at an event.</p></div>');
            if (EF.isManager()) {
                await fetchAllEvents();
                const events = manageableEvents();
                html += '<h3 class="ef-section-title">Certificate status by event</h3>' + (events.length ? '<div class="ef-toolbar"><label for="certEvent">Event</label><select id="certEvent" class="form-select">' +
                    eventOptions(events.some((e) => e.id === EF.certEventId) ? EF.certEventId : events[0].id) + '</select></div><div id="certStatus"></div>' : '<div class="ef-state"><p>No events to show.</p></div>');
            }
            host.innerHTML = html;
            const sel = $('certEvent');
            if (sel) { sel.addEventListener('change', () => EF.loadCertStatus(parseInt(sel.value, 10))); EF.loadCertStatus(parseInt(sel.value, 10)); }
        } catch (e) {
            setError(host, e.message, 'EF.loadCertificates()');
        }
    };

    EF.loadCertStatus = async function (eventId) {
        EF.certEventId = eventId;
        const host = $('certStatus');
        setLoading(host);
        try {
            const s = await EF.api('/api/events/' + eventId + '/certificates');
            const badge = { ISSUED: 'badge-success', PENDING: 'badge-warning', NOT_ATTENDED: 'badge-danger' };
            const label = { ISSUED: 'Issued', PENDING: 'Pending', NOT_ATTENDED: 'Did not attend' };
            host.innerHTML = '<div class="stats-grid"><div class="stat-card"><div class="stat-value">' + s.eligible + '</div><div class="stat-label">Attended</div></div><div class="stat-card"><div class="stat-value">' + s.issued +
                '</div><div class="stat-label">Certificates issued</div></div><div class="stat-card"><div class="stat-value">' + s.notAttended + '</div><div class="stat-label">Did not attend</div></div></div>' +
                '<div class="card"><div class="card-header"><h3 class="card-title">Participants</h3><button class="btn btn-outline btn-sm" onclick="EF.generateCerts(' + eventId + ')"><i class="fas fa-rotate"></i> Generate missing</button></div>' +
                (s.rows.length ? '<div class="ef-table-wrap"><table><thead><tr><th>Participant</th><th>Status</th><th>Certificate ID</th><th>Issued</th><th></th></tr></thead><tbody>' + s.rows.map((r) =>
                    '<tr><td><strong>' + esc(r.participantName) + '</strong><div class="ef-muted" style="font-size:.75rem">' + esc(r.participantEmail) + '</div></td><td><span class="badge ' + badge[r.status] + '">' + label[r.status] + '</span></td><td><code>' + esc(r.certificateCode || '-') +
                    '</code></td><td>' + (r.issuedAt ? fmtDateTime(r.issuedAt) : '-') + '</td><td>' + (r.certificateId ? '<a class="btn btn-icon btn-sm" title="Download" href="/api/certificates/' + r.certificateId + '/download" download><i class="fas fa-download"></i></a>' : '') + '</td></tr>').join('') + '</tbody></table></div>'
                    : '<div class="ef-state"><p>No confirmed registrations for this event.</p></div>') + '</div>';
        } catch (e) {
            setError(host, e.message, 'EF.loadCertStatus(' + eventId + ')');
        }
    };

    EF.generateCerts = async function (eventId) {
        try {
            const r = await EF.api('/api/events/' + eventId + '/certificates/generate', { method: 'POST' });
            EF.toast(r.created + ' certificate(s) generated.', 'success');
        } catch (e) { EF.toast(e.message, 'error'); }
        EF.loadCertStatus(eventId);
    };

    // ------------------------------------------------------------------ users (admin)
    EF.loadUsers = async function () {
        const host = $('usersContent');
        setLoading(host);
        try {
            const users = await EF.api('/api/admin/users');
            host.innerHTML = '<div class="card"><div class="card-header"><h3 class="card-title">Create account</h3></div><form id="userForm" class="ef-inline-form">' +
                '<div class="form-group"><label class="form-label">Name</label><input class="form-input" name="name" required maxlength="120"></div>' +
                '<div class="form-group"><label class="form-label">Email</label><input class="form-input" type="email" name="email" required maxlength="190"></div>' +
                '<div class="form-group"><label class="form-label">Password (min 8)</label><input class="form-input" type="password" name="password" required minlength="8" maxlength="72" autocomplete="new-password"></div>' +
                '<div class="form-group"><label class="form-label">Role</label><select class="form-select" name="role"><option value="ORGANIZER">Organizer</option><option value="PARTICIPANT">Participant</option><option value="ADMIN">Admin</option></select></div>' +
                '<div class="form-group"><button class="btn btn-primary" type="submit"><i class="fas fa-user-plus"></i> Create</button></div></form></div>' +
                '<div class="card" style="margin-top:1.5rem"><div class="card-header"><h3 class="card-title">Accounts (' + users.length + ')</h3></div><div class="ef-table-wrap"><table><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Created</th></tr></thead><tbody>' +
                users.map((u) => '<tr><td><strong>' + esc(u.name) + '</strong></td><td>' + esc(u.email) + '</td><td><span class="badge badge-primary">' + esc(u.role) + '</span></td><td>' + fmtDateTime(u.createdAt) + '</td></tr>').join('') + '</tbody></table></div></div>';
            $('userForm').addEventListener('submit', async (e) => {
                e.preventDefault();
                const f = e.target;
                try {
                    await EF.api('/api/admin/users', { method: 'POST', body: { name: f.name.value, email: f.email.value, password: f.password.value, role: f.role.value } });
                    EF.toast('Account created.', 'success');
                    EF.loadUsers();
                } catch (err) { EF.toast(err.message, 'error'); }
            });
        } catch (e) {
            setError(host, e.message, 'EF.loadUsers()');
        }
    };

    // ------------------------------------------------------------------ analytics insights
    EF.loadInsights = async function () {
        const host = $('insightsContent');
        if (!host) return;
        setLoading(host, 'Loading registration analytics...');
        Object.values(EF.charts).forEach((c) => c && c.destroy());
        EF.charts = {};
        try {
            const o = await EF.api('/api/analytics/overview');
            const card = (v, l, icon, cls) => '<div class="stat-card"><div class="stat-header"><div><div class="stat-value">' + v + '</div><div class="stat-label">' + l + '</div></div><div class="stat-icon ' + cls + '"><i class="fas ' + icon + '"></i></div></div></div>';
            let html = '<div class="stats-grid">' + card(o.totalEvents, 'Events', 'fa-calendar', 'primary') + card(o.totalRegistrations, 'Registered', 'fa-user-plus', 'primary') + card(o.confirmedRegistrations, 'Confirmed', 'fa-circle-check', 'success') +
                card(o.waitlistedRegistrations, 'Waitlisted', 'fa-hourglass-half', 'warning') + card(o.totalAttendees, 'Attendees', 'fa-qrcode', 'success') + card(o.attendancePercent + '%', 'Attendance rate', 'fa-percent', 'primary') + card(o.availableSeats, 'Seats available', 'fa-chair', 'warning') + '</div>';
            if (!o.totalRegistrations) {
                host.innerHTML = html + '<div class="card"><div class="ef-state"><i class="fas fa-chart-column"></i><h3>No registrations yet</h3><p>Charts appear once participants register for your events.</p></div></div>';
                return;
            }
            html += '<div class="chart-grid">' + ['insRegChart:Registrations per event', 'insAttChart:Attendance vs registrations', 'insTrendChart:Registration trend', 'insPopChart:Most popular events'].map((x) => {
                const p = x.split(':');
                return '<div class="card"><div class="card-header"><h3 class="card-title">' + p[1] + '</h3></div><div class="chart-container"><canvas id="' + p[0] + '"></canvas></div></div>';
            }).join('') + '</div>' +
                '<div class="card" style="margin-top:1.5rem"><div class="card-header"><h3 class="card-title">Event-wise statistics</h3></div><div class="ef-table-wrap"><table><thead><tr><th>Event</th><th>Status</th><th>Capacity</th><th>Confirmed</th><th>Waitlisted</th><th>Cancelled</th><th>Attended</th><th>Seats left</th></tr></thead><tbody>' +
                o.events.map((e) => '<tr><td><strong>' + esc(e.name) + '</strong></td><td>' + getStatusBadge(e.status) + '</td><td>' + e.capacity + '</td><td>' + e.confirmed + '</td><td>' + e.waitlisted + '</td><td>' + e.cancelled + '</td><td>' + e.checkedIn + ' (' + e.attendancePercent + '%)</td><td>' + e.availableSeats + '</td></tr>').join('') + '</tbody></table></div></div>';
            host.innerHTML = html;

            const dark = document.body.classList.contains('dark-mode');
            Chart.defaults.color = dark ? '#cbd5e1' : '#475569';
            const grid = dark ? '#334155' : '#e2e8f0';
            const C = { primary: '#6366f1', success: '#10b981', warning: '#f59e0b' };
            const short = (n) => (n.length > 22 ? n.slice(0, 21) + '…' : n);
            const base = { responsive: true, maintainAspectRatio: false, scales: { y: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: grid } }, x: { grid: { display: false } } } };
            const evs = o.events.filter((e) => e.confirmed + e.waitlisted > 0);
            EF.charts.reg = new Chart($('insRegChart'), { type: 'bar', data: { labels: evs.map((e) => short(e.name)), datasets: [{ label: 'Confirmed', data: evs.map((e) => e.confirmed), backgroundColor: C.primary }, { label: 'Waitlisted', data: evs.map((e) => e.waitlisted), backgroundColor: C.warning }] }, options: Object.assign({}, base, { scales: { x: { stacked: true, grid: { display: false } }, y: { stacked: true, beginAtZero: true, ticks: { precision: 0 }, grid: { color: grid } } } }) });
            EF.charts.att = new Chart($('insAttChart'), { type: 'bar', data: { labels: evs.map((e) => short(e.name)), datasets: [{ label: 'Confirmed', data: evs.map((e) => e.confirmed), backgroundColor: C.primary }, { label: 'Attended', data: evs.map((e) => e.checkedIn), backgroundColor: C.success }] }, options: base });
            EF.charts.trend = new Chart($('insTrendChart'), { type: 'line', data: { labels: o.registrationTrend.map((p) => formatDate(p.date)), datasets: [{ label: 'New registrations', data: o.registrationTrend.map((p) => p.registrations), borderColor: C.primary, backgroundColor: C.primary, tension: 0.25, pointRadius: 4 }] }, options: base });
            EF.charts.pop = new Chart($('insPopChart'), { type: 'bar', data: { labels: o.popularEvents.map((e) => short(e.name)), datasets: [{ label: 'Registrations (confirmed + waitlisted)', data: o.popularEvents.map((e) => e.confirmed + e.waitlisted), backgroundColor: C.primary }] }, options: Object.assign({}, base, { indexAxis: 'y', scales: { x: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: grid } }, y: { grid: { display: false } } } }) });
        } catch (e) {
            setError(host, e.message, 'EF.loadInsights()');
        }
    };
})();
