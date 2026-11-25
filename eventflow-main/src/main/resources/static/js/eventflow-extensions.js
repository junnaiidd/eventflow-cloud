/* EventFlow extensions: authentication, role-based UI, registration/waitlist, QR tickets, check-in,
   certificates, user admin and analytics insights. Loaded after the main script in index.html and
   exposed as window.EF. All permission checks here only hide buttons: the server enforces every rule. */
(function () {
    'use strict';

    const EF = { user: null, myRegs: null, ci: { eventId: null, stream: null, scanning: false, busy: false, lastCode: null, lastTime: 0, detector: null, recent: [] }, charts: {} };
    window.EF = EF;
    const $ = (id) => document.getElementById(id);
    const OPEN = ['Upcoming', 'In Progress'];
    const PAGE_ROLES = { create: ['ORGANIZER', 'ADMIN'], analytics: ['ORGANIZER', 'ADMIN'], checkin: ['ORGANIZER', 'ADMIN'], users: ['ADMIN'], approvals: ['ADMIN'] };
    const ROLE_LABELS = { PARTICIPANT: 'Student', ORGANIZER: 'Organizer', ADMIN: 'Admin' };
    const STATUS_LABELS = { 'Upcoming': 'Published', 'In Progress': 'Ongoing', 'Pending Approval': 'Pending Approval', 'Rejected': 'Rejected' };

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
    // The internal role PARTICIPANT is always shown to people as "Student".
    EF.roleLabel = (role) => ROLE_LABELS[role] || role;
    EF.statusLabel = (status) => STATUS_LABELS[status] || status;
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
        $('efUserRole').textContent = EF.roleLabel(EF.user.role) + (EF.user.department && EF.isParticipant() ? ' · ' + EF.user.department : '');
        const tl = $('navTicketsLabel');
        if (tl) tl.textContent = 'My Registrations';
        EF.refreshApprovalCount();
        $('efUserAvatar').textContent = EF.user.name.charAt(0).toUpperCase();
    }

    // ------------------------------------------------------------------ college data (departments, categories)
    EF.meta = { collegeName: '', departments: [], categories: [], years: [1, 2, 3, 4] };

    function fillSelects() {
        document.querySelectorAll('select[data-fill]').forEach((sel) => {
            const kind = sel.dataset.fill;
            const keep = sel.value;
            const first = sel.querySelector('option[value=""]');
            sel.innerHTML = '';
            if (first) sel.appendChild(first);
            const values = kind === 'years' ? EF.meta.years : EF.meta[kind];
            values.forEach((v) => {
                const o = document.createElement('option');
                o.value = v;
                o.textContent = kind === 'years' ? 'Year ' + v : v;
                sel.appendChild(o);
            });
            if (keep) sel.value = keep;
        });
        const cat = $('filterCategory');
        const dep = $('filterDepartment');
        const st = $('filterStatus');
        if (cat && cat.options.length <= 1) EF.meta.categories.forEach((c) => cat.add(new Option(c, c)));
        if (dep && dep.options.length <= 1) EF.meta.departments.forEach((d) => dep.add(new Option(d, d)));
        if (st && st.options.length <= 1) ['Upcoming', 'In Progress', 'Completed', 'Cancelled', 'Pending Approval', 'Rejected'].forEach((x) => st.add(new Option(EF.statusLabel(x), x)));
    }

    EF.loadMeta = async function () {
        try {
            EF.meta = Object.assign(EF.meta, await EF.api('/api/meta'));
        } catch (e) { /* forms keep working with the lists they already have */ }
        fillSelects();
        const name = EF.meta.collegeName;
        if (name) {
            document.title = 'EventFlow - ' + name;
            const sub = $('authSub');
            if (sub) sub.textContent = name + ' · College Event Management System. Sign in to discover events, register and track attendance.';
        }
    };

    // A select must be able to show legacy values (for example an old event type that is not a college category).
    EF.setSelectValue = function (sel, value) {
        if (!sel) return;
        if (value && ![...sel.options].some((o) => o.value === value)) sel.add(new Option(value + ' (legacy)', value));
        sel.value = value || '';
    };

    EF.prepareStatusSelect = function (sel, event) {
        sel.querySelectorAll('option[data-extra]').forEach((o) => o.remove());
        const locked = event.status === 'Pending Approval' || event.status === 'Rejected';
        if (locked) {
            const o = new Option(EF.statusLabel(event.status) + ' (set by admin approval)', event.status);
            o.dataset.extra = '1';
            sel.add(o);
        }
        sel.value = event.status;
        sel.disabled = locked;
    };

    // ------------------------------------------------------------------ event list filters
    EF.applyFilters = function (events) {
        const val = (id) => ($(id) ? $(id).value : '');
        const c = val('filterCategory'), d = val('filterDepartment'), st = val('filterStatus');
        return events.filter((e) => (!c || e.type === c) && (!d || e.department === d) && (!st || e.status === st));
    };
    EF.clearFilters = function () {
        ['filterCategory', 'filterDepartment', 'filterStatus'].forEach((id) => { if ($(id)) $(id).value = ''; });
        const q = $('searchInput');
        if (q) q.value = '';
        renderAllEvents(allEvents);
    };
    EF.wireFilters = function () {
        ['filterCategory', 'filterDepartment', 'filterStatus'].forEach((id) => {
            if ($(id)) $(id).addEventListener('change', () => {
                const q = $('searchInput');
                if (q && q.value.trim()) q.dispatchEvent(new Event('input'));
                else renderAllEvents(allEvents);
            });
        });
    };

    // ------------------------------------------------------------------ sign in / out
    EF.showLogin = function (message) {
        EF.user = null;
        EF.stopCamera();
        if (typeof EF.authTab === 'function') EF.authTab('login');
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

    // Client-side checks only mirror the server rules to give instant feedback; the server remains authoritative.
    const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    function fieldError(input, msg) {
        const group = input.closest('.form-group');
        let el = group.querySelector('.ef-field-error');
        if (!msg) {
            if (el) el.remove();
            input.removeAttribute('aria-invalid');
            input.removeAttribute('aria-describedby');
            return;
        }
        if (!el) {
            el = document.createElement('div');
            el.className = 'ef-field-error';
            el.id = input.id + 'Err';
            group.appendChild(el);
        }
        el.textContent = msg;
        input.setAttribute('aria-invalid', 'true');
        input.setAttribute('aria-describedby', el.id);
    }

    function validateAuth(mode, form) {
        const rules = [['email', (v) => (!v.trim() ? 'Enter your email address.' : !EMAIL_RE.test(v.trim()) ? 'Enter a valid email address.' : '')]];
        if (mode === 'login') {
            rules.push(['password', (v) => (v ? '' : 'Enter your password.')]);
        } else {
            rules.unshift(['name', (v) => (v.trim() ? '' : 'Enter your full name.')]);
            rules.push(['password', (v) => (v.length < 8 || v.length > 72 ? 'Password must be 8 to 72 characters.' : '')]);
            rules.push(['usn', (v) => (/^[A-Za-z0-9]{5,20}$/.test(v.trim()) ? '' : 'USN must be 5 to 20 letters or digits.')]);
            rules.push(['department', (v) => (v ? '' : 'Choose your department.')]);
            rules.push(['yearOfStudy', (v) => (v ? '' : 'Choose your year of study.')]);
        }
        let first = null;
        rules.forEach(([name, check]) => {
            const input = form.elements[name];
            const msg = check(input.value);
            fieldError(input, msg);
            if (msg && !first) first = input;
        });
        if (first) first.focus();
        return !first;
    }

    function setBusy(btn, state, text) {
        const label = btn.querySelector('.ef-btn-label');
        if (!btn.dataset.label) btn.dataset.label = label.textContent;
        btn.classList.toggle('is-loading', state === 'loading');
        btn.classList.toggle('is-success', state === 'success');
        btn.disabled = state === 'loading' || state === 'success';
        label.textContent = text || btn.dataset.label;
    }

    async function submitAuth(mode, form) {
        const err = $('authError');
        err.textContent = '';
        if (!validateAuth(mode, form)) return;
        const btn = form.querySelector('button[type=submit]');
        setBusy(btn, 'loading', mode === 'login' ? 'Signing in...' : 'Creating your account...');
        try {
            const body = mode === 'login'
                ? { email: form.email.value, password: form.password.value }
                : { name: form.name.value, email: form.email.value, password: form.password.value,
                    usn: form.usn.value.trim().toUpperCase(), department: form.department.value, yearOfStudy: parseInt(form.yearOfStudy.value, 10) };
            EF.user = await EF.api(mode === 'login' ? '/api/auth/login' : '/api/auth/register', { method: 'POST', body });
            setBusy(btn, 'success', mode === 'login' ? 'Welcome back!' : 'Account created!');
            await new Promise((r) => setTimeout(r, 450));
            form.reset();
            onLoggedIn();
            if (typeof fetchAllEvents === 'function') await fetchAllEvents();
            showPage('dashboard');
        } catch (e) {
            err.textContent = e.message;
        } finally {
            setBusy(btn, 'idle');
        }
    }

    const AUTH_COPY = {
        login: { heading: 'Welcome back', switchHtml: 'New to EventFlow? <button type="button" class="ef-link" onclick="EF.authTab(\'signup\')">Create an account</button>' },
        signup: { heading: 'Join your campus', switchHtml: 'Already have an account? <button type="button" class="ef-link" onclick="EF.authTab(\'login\')">Sign in</button>' },
    };

    EF.authTab = function (mode) {
        const login = $('loginForm'), signup = $('signupForm');
        const shown = mode === 'login' ? login : signup;
        login.classList.toggle('hidden', mode !== 'login');
        signup.classList.toggle('hidden', mode !== 'signup');
        $('tabLogin').classList.toggle('active', mode === 'login');
        $('tabSignup').classList.toggle('active', mode === 'signup');
        $('tabLogin').setAttribute('aria-selected', String(mode === 'login'));
        $('tabSignup').setAttribute('aria-selected', String(mode === 'signup'));
        document.querySelector('.ef-tabs').dataset.mode = mode;
        $('authHeading').textContent = AUTH_COPY[mode].heading;
        $('authSwitch').innerHTML = AUTH_COPY[mode].switchHtml;
        $('authError').textContent = '';
        document.querySelectorAll('#authScreen .ef-field-error').forEach((el) => el.remove());
        document.querySelectorAll('#authScreen [aria-invalid]').forEach((el) => el.removeAttribute('aria-invalid'));
        shown.classList.remove('ef-enter');
        void shown.offsetWidth; // restart the entrance animation
        shown.classList.add('ef-enter');
    };

    EF.init = function () {
        $('loginForm').addEventListener('submit', (e) => { e.preventDefault(); submitAuth('login', e.target); });
        $('signupForm').addEventListener('submit', (e) => { e.preventDefault(); submitAuth('signup', e.target); });
        document.querySelectorAll('#authScreen [data-toggle-pw]').forEach((btn) => btn.addEventListener('click', () => {
            const input = btn.parentElement.querySelector('input');
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            btn.setAttribute('aria-pressed', String(show));
            btn.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
            btn.firstElementChild.className = show ? 'fas fa-eye-slash' : 'fas fa-eye';
        }));
        document.querySelectorAll('#authScreen input, #authScreen select').forEach((el) => {
            el.addEventListener('input', () => { if (el.hasAttribute('aria-invalid')) fieldError(el, ''); });
            el.addEventListener('change', () => { if (el.hasAttribute('aria-invalid')) fieldError(el, ''); });
        });
        EF.wireFilters();
        EF.loadMeta();
    };

    // called by showPage() in index.html
    EF.onPageShown = function (page) {
        if (page !== 'checkin') EF.stopCamera();
        if (page === 'tickets') EF.loadTickets();
        if (page === 'checkin') EF.loadCheckin();
        if (page === 'certificates') EF.loadCertificates();
        if (page === 'users') EF.loadUsers();
        if (page === 'approvals') EF.loadApprovals();
        if (page === 'profile') EF.loadProfile();
        if (page === 'events') EF.eventsHeading();
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
        const today = new Date().toISOString().slice(0, 10);
        const pastDeadline = event.registrationDeadline && event.registrationDeadline < today;
        let body;
        if (!mine && open && !EF.user.usn) {
            body = '<p>Complete your student profile (USN, department and year) before registering for events.</p><div class="ef-actions"><button class="btn btn-primary btn-sm" onclick="closeModal(\'eventDetailModal\'); showPage(\'profile\')">Complete profile</button></div>';
        } else if (!mine && open && pastDeadline) {
            body = '<p>The registration deadline (' + formatDate(event.registrationDeadline) + ') has passed.</p>';
        } else if (mine && mine.status === 'CONFIRMED') {
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
            body = (event.registrationDeadline ? '<p class="ef-muted">Registration closes on ' + formatDate(event.registrationDeadline) + '.</p>' : '') + meter(event) + '<div class="ef-actions"><button class="btn btn-primary" onclick="EF.register(' + event.id + ')"><i class="fas fa-ticket"></i> Register now</button></div>';
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
            const today = new Date().toISOString().slice(0, 10);
            const open = events.filter((e) => OPEN.includes(e.status) && !(e.registrationDeadline && e.registrationDeadline < today && !regs.some((r) => r.eventId === e.id && r.status !== 'CANCELLED')))
                .sort((a, b) => new Date(a.eventDate) - new Date(b.eventDate)).slice(0, 6);
            $('dashTitle').textContent = 'Student Dashboard';
            $('dashSubtitle').textContent = 'Hello ' + EF.user.name + '! Discover college events, manage your registrations and track your participation.';
            const regFor = (id) => regs.find((r) => r.eventId === id && r.status !== 'CANCELLED');
            host.innerHTML = '<div class="stats-grid">' +
                stat(regs.filter((r) => r.status === 'CONFIRMED').length, 'Confirmed tickets', 'fa-ticket', 'primary') +
                stat(regs.filter((r) => r.status === 'WAITLISTED').length, 'On waitlist', 'fa-hourglass-half', 'warning') +
                stat(regs.filter((r) => r.checkedIn).length, 'Events attended', 'fa-check-circle', 'success') +
                stat(certs, 'Certificates', 'fa-certificate', 'primary') + '</div>' +
                profilePrompt() + myRegistrationsCard(regs, events) +
                '<div class="card" style="margin-top:1.5rem"><div class="card-header"><h3 class="card-title">Upcoming &amp; available events</h3><button class="btn btn-outline btn-sm" onclick="showPage(\'events\')">All events</button></div>' +
                (open.length ? '<div class="ef-event-grid">' + open.map((e) => {
                    const r = regFor(e.id);
                    const status = r ? EF.regBadge(r) : (e.availableSeats > 0 ? '<span class="badge badge-success">' + e.availableSeats + ' seats left</span>' : (e.capacity ? '<span class="badge badge-warning">Full · waitlist</span>' : '<span class="badge badge-primary">Not open</span>'));
                    const deadline = e.registrationDeadline ? '<span><i class="fas fa-hourglass-end"></i> Register by ' + formatDate(e.registrationDeadline) + '</span>' : '';
                    return '<article class="ef-event-card">' + (e.posterUrl ? EF.posterHtml(e, 'ef-poster') : '<div class="ef-poster-empty"><i class="fas fa-calendar-days"></i></div>') +
                        '<div class="ef-event-body"><div class="ef-event-title">' + esc(e.name) + '</div><div class="ef-event-meta"><span><i class="fas fa-calendar"></i> ' + formatDate(e.eventDate) + (e.eventTime ? ' · ' + esc(e.eventTime) : '') +
                        '</span><span><i class="fas fa-location-dot"></i> ' + esc(e.location) + '</span>' + deadline + '</div><div class="ef-event-badges"><span class="badge badge-primary">' + esc(e.type || '') + '</span>' + (e.department ? '<span class="badge badge-success">' + esc(e.department) + '</span>' : '') + '</div><div class="ef-event-foot">' + status +
                        '<button class="btn btn-outline btn-sm" onclick="viewEvent(' + e.id + ')">Details</button></div></div></article>';
                }).join('') + '</div>' : '<div class="ef-state"><i class="fas fa-calendar-xmark"></i><h3>No open events</h3><p>New events will appear here when organizers publish them.</p></div>') + '</div>';
        } catch (e) {
            setError(host, e.message, 'EF.loadParticipantDashboard()');
        }
    };

    function profilePrompt() {
        if (EF.user.usn && EF.user.department && EF.user.yearOfStudy) return '';
        return '<div class="ef-banner"><i class="fas fa-circle-info"></i><div><strong>Complete your student profile.</strong> Add your USN, department and year of study to register for events.</div>' +
            '<button class="btn btn-primary btn-sm" onclick="showPage(\'profile\')">Update profile</button></div>';
    }

    // "My registrations" = upcoming events I hold a seat or waitlist spot for; history = events already attended or past.
    function myRegistrationsCard(regs, events) {
        const today = new Date().toISOString().slice(0, 10);
        const byId = {};
        events.forEach((e) => { byId[e.id] = e; });
        const isPast = (r) => r.checkedIn || (r.eventDate && r.eventDate < today) || (byId[r.eventId] && ['Completed', 'Cancelled'].includes(byId[r.eventId].status));
        const current = regs.filter((r) => r.status !== 'CANCELLED' && !isPast(r));
        const history = regs.filter((r) => r.status !== 'CANCELLED' && isPast(r));
        const row = (r, withTicket) => '<tr><td><strong>' + esc(r.eventName) + '</strong></td><td>' + formatDate(r.eventDate) + '</td><td>' + esc(r.location || '') + '</td><td>' + EF.regBadge(r) + '</td><td>' +
            (withTicket ? '<button class="btn btn-outline btn-sm" onclick="showPage(\'tickets\')"><i class="fas fa-qrcode"></i> Ticket</button>' : (r.certificateId ? '<a class="btn btn-outline btn-sm" href="/api/certificates/' + r.certificateId + '/download" download><i class="fas fa-certificate"></i> Certificate</a>' : '<span class="ef-muted">-</span>')) + '</td></tr>';
        const table = (rows, last, withTicket) => '<div class="table-container"><table><thead><tr><th>Event</th><th>Date</th><th>Venue</th><th>Status</th><th>' + last + '</th></tr></thead><tbody>' + rows.map((r) => row(r, withTicket)).join('') + '</tbody></table></div>';
        return '<div class="card" style="margin-top:1.5rem"><div class="card-header"><h3 class="card-title">My registrations</h3><button class="btn btn-outline btn-sm" onclick="showPage(\'tickets\')">All tickets</button></div>' +
            (current.length ? table(current, 'QR ticket', true) : '<div class="ef-state"><p>You have no upcoming registrations. Pick an event below to register.</p></div>') + '</div>' +
            '<div class="card" style="margin-top:1.5rem"><div class="card-header"><h3 class="card-title">Participation history</h3></div>' +
            (history.length ? table(history, 'Certificate', false) : '<div class="ef-state"><p>Events you have attended will be listed here.</p></div>') + '</div>';
    }

    // ------------------------------------------------------------------ dashboards for organizers and admins
    EF.dashboardHeading = function () {
        const admin = EF.role() === 'ADMIN';
        $('dashTitle').textContent = admin ? 'Admin Dashboard' : 'Organizer Dashboard';
        $('dashSubtitle').textContent = admin ? 'Overview of students, organizers, events and registrations across the college.' : 'Create and manage your college events and track student participation.';
        const card = $('orgUpcomingCard');
        if (card) card.querySelector('.card-title').textContent = 'Upcoming events';
    };

    EF.eventsHeading = function () {
        const el = $('eventsSubtitle');
        if (!el) return;
        el.textContent = EF.isParticipant() ? 'Browse, search and filter published college events.' : (EF.role() === 'ADMIN' ? 'Monitor all college events.' : 'Browse events and manage the ones you organize.');
    };

    EF.afterDashboard = async function () {
        const adminHost = $('adminOverview');
        adminHost.innerHTML = '';
        if (EF.role() === 'ADMIN') {
            try {
                const a = await EF.api('/api/admin/stats');
                const st = (v, l, icon, cls) => '<div class="stat-card"><div class="stat-header"><div><div class="stat-value">' + v + '</div><div class="stat-label">' + l + '</div></div><div class="stat-icon ' + cls + '"><i class="fas ' + icon + '"></i></div></div></div>';
                adminHost.innerHTML = '<div class="stats-grid">' + st(a.students, 'Students', 'fa-user-graduate', 'primary') + st(a.organizers, 'Organizers', 'fa-user-tie', 'primary') +
                    st(a.totalEvents, 'Total events', 'fa-calendar', 'success') + st(a.totalRegistrations, 'Registrations', 'fa-user-plus', 'success') +
                    st(a.upcomingEvents, 'Upcoming events', 'fa-clock', 'warning') + st(a.pendingApprovals, 'Pending approvals', 'fa-clipboard-check', a.pendingApprovals ? 'danger' : 'warning') + '</div>' +
                    (a.pendingApprovals ? '<div class="ef-banner"><i class="fas fa-clipboard-check"></i><div><strong>' + a.pendingApprovals + ' event' + (a.pendingApprovals > 1 ? 's' : '') + ' waiting for approval.</strong></div><button class="btn btn-primary btn-sm" onclick="showPage(\'approvals\')">Review</button></div>' : '');
                EF.setApprovalCount(a.pendingApprovals);
            } catch (e) {
                setError(adminHost, e.message, 'EF.afterDashboard()');
            }
        }
        $('participantDashboard').classList.add('hidden');
        ['dashboardStats', 'dashboardRegStats', 'orgUpcomingCard'].forEach((id) => $(id).classList.remove('hidden'));
        const host = $('dashboardRegStats');
        if (EF.role() === 'ORGANIZER') EF.organizerPending();
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
            '<dl class="ef-meta"><dt>Student</dt><dd>' + esc(r.participantName) + '</dd><dt>Date</dt><dd>' + formatDate(r.eventDate) + (r.eventTime ? ' · ' + esc(r.eventTime) : '') + '</dd>' +
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
            '<div class="ef-two-col"><div class="card"><div class="card-header"><h3 class="card-title">Scan student ticket</h3><button class="btn btn-primary btn-sm" id="ciCamBtn"><i class="fas fa-camera"></i> Start camera</button></div>' +
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
            roster.innerHTML = rows.length ? '<table><thead><tr><th>Student</th><th>USN</th><th>Department / Year</th><th>Status</th><th>Ticket</th><th>Checked in</th></tr></thead><tbody>' +
                rows.map((r) => '<tr><td><strong>' + esc(r.participantName) + '</strong><div class="ef-muted" style="font-size:.75rem">' + esc(r.participantEmail) + '</div></td><td>' + esc(r.usn || '-') + '</td><td>' + esc(r.department || '-') + (r.yearOfStudy ? ' · Year ' + r.yearOfStudy : '') + '</td><td>' + EF.regBadge(r) + '</td><td><code>' + esc(r.ticketId || '-') + '</code></td><td>' + (r.checkedIn ? fmtDateTime(r.checkedInAt) : '-') + '</td></tr>').join('') + '</tbody></table>' :
                '<div class="ef-state"><i class="fas fa-user-slash"></i><h3>No registrations yet</h3><p>Students will appear here once they register.</p></div>';
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
            (result.participantName || result.eventName ? '<dl class="ef-meta">' + (result.participantName ? '<dt>Student</dt><dd>' + esc(result.participantName) + '</dd>' : '') +
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
                '<div class="card"><div class="card-header"><h3 class="card-title">Students</h3><button class="btn btn-outline btn-sm" onclick="EF.generateCerts(' + eventId + ')"><i class="fas fa-rotate"></i> Generate missing</button></div>' +
                (s.rows.length ? '<div class="ef-table-wrap"><table><thead><tr><th>Student</th><th>Status</th><th>Certificate ID</th><th>Issued</th><th></th></tr></thead><tbody>' + s.rows.map((r) =>
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
            EF.userList = users;
            const yearOpts = EF.meta.years.map((y) => '<option value="' + y + '">Year ' + y + '</option>').join('');
            const deptOpts = EF.meta.departments.map((d) => '<option value="' + esc(d) + '">' + esc(d) + '</option>').join('');
            host.innerHTML = '<div class="card"><div class="card-header"><h3 class="card-title">Create account</h3></div><form id="userForm" class="ef-inline-form">' +
                '<div class="form-group"><label class="form-label">Name</label><input class="form-input" name="name" required maxlength="120"></div>' +
                '<div class="form-group"><label class="form-label">Email</label><input class="form-input" type="email" name="email" required maxlength="190"></div>' +
                '<div class="form-group"><label class="form-label">Password (min 8)</label><input class="form-input" type="password" name="password" required minlength="8" maxlength="72" autocomplete="new-password"></div>' +
                '<div class="form-group"><label class="form-label">Role</label><select class="form-select" name="role" id="userRole"><option value="ORGANIZER">Organizer</option><option value="PARTICIPANT">Student</option><option value="ADMIN">Admin</option></select></div>' +
                '<div class="form-group ef-student-only hidden"><label class="form-label">USN</label><input class="form-input" name="usn" maxlength="20" pattern="[A-Za-z0-9]{5,20}" title="5 to 20 letters or digits" style="text-transform:uppercase"></div>' +
                '<div class="form-group ef-student-only hidden"><label class="form-label">Department</label><select class="form-select" name="department"><option value="">Select...</option>' + deptOpts + '</select></div>' +
                '<div class="form-group ef-student-only hidden"><label class="form-label">Year</label><select class="form-select" name="yearOfStudy"><option value="">Select...</option>' + yearOpts + '</select></div>' +
                '<div class="form-group"><button class="btn btn-primary" type="submit"><i class="fas fa-user-plus"></i> Create</button></div></form></div>' +
                '<div class="card" style="margin-top:1.5rem"><div class="card-header"><h3 class="card-title" id="userCount"></h3></div>' +
                '<div class="ef-filters"><input class="form-input" id="userSearch" placeholder="Search name, email or USN" aria-label="Search users">' +
                '<select class="form-select" id="userRoleFilter" aria-label="Filter by role"><option value="">All roles</option><option value="PARTICIPANT">Students</option><option value="ORGANIZER">Organizers</option><option value="ADMIN">Admins</option></select>' +
                '<select class="form-select" id="userDeptFilter" aria-label="Filter by department"><option value="">All departments</option>' + deptOpts + '</select></div>' +
                '<div class="ef-table-wrap"><table><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>USN</th><th>Department</th><th>Year</th><th>Status</th><th>Actions</th></tr></thead><tbody id="userRows"></tbody></table></div></div>';
            const roleSel = $('userRole');
            const toggleStudent = () => document.querySelectorAll('.ef-student-only').forEach((el) => el.classList.toggle('hidden', roleSel.value !== 'PARTICIPANT'));
            roleSel.addEventListener('change', toggleStudent);
            ['userSearch', 'userRoleFilter', 'userDeptFilter'].forEach((id) => $(id).addEventListener(id === 'userSearch' ? 'input' : 'change', EF.renderUsers));
            EF.renderUsers();
            $('userForm').addEventListener('submit', async (e) => {
                e.preventDefault();
                const f = e.target;
                const body = { name: f.name.value, email: f.email.value, password: f.password.value, role: f.role.value };
                if (f.role.value === 'PARTICIPANT') {
                    body.usn = f.usn.value.trim().toUpperCase();
                    body.department = f.department.value;
                    body.yearOfStudy = f.yearOfStudy.value ? parseInt(f.yearOfStudy.value, 10) : null;
                }
                try {
                    await EF.api('/api/admin/users', { method: 'POST', body });
                    EF.toast('Account created.', 'success');
                    EF.loadUsers();
                } catch (err) { EF.toast(err.message, 'error'); }
            });
        } catch (e) {
            setError(host, e.message, 'EF.loadUsers()');
        }
    };

    EF.renderUsers = function () {
        const q = ($('userSearch').value || '').trim().toLowerCase();
        const role = $('userRoleFilter').value;
        const dept = $('userDeptFilter').value;
        const rows = (EF.userList || []).filter((u) => (!role || u.role === role) && (!dept || u.department === dept) &&
            (!q || [u.name, u.email, u.usn].some((v) => v && v.toLowerCase().includes(q))));
        $('userCount').textContent = 'Accounts (' + rows.length + ' of ' + (EF.userList || []).length + ')';
        $('userRows').innerHTML = rows.length ? rows.map((u) => {
            const self = EF.user && u.id === EF.user.id;
            return '<tr><td><strong>' + esc(u.name) + '</strong></td><td>' + esc(u.email) + '</td><td><span class="badge badge-primary">' + esc(EF.roleLabel(u.role)) + '</span></td><td>' + esc(u.usn || '-') + '</td><td>' + esc(u.department || '-') + '</td><td>' + (u.yearOfStudy || '-') + '</td>' +
                '<td>' + (u.active ? '<span class="badge badge-success">Active</span>' : '<span class="badge badge-danger">Deactivated</span>') + '</td><td>' +
                (self ? '<span class="ef-muted">You</span>' : '<button class="btn btn-outline btn-sm" onclick="EF.toggleUser(' + u.id + ',' + (!u.active) + ')">' + (u.active ? 'Deactivate' : 'Activate') + '</button>') + '</td></tr>';
        }).join('') : '<tr><td colspan="8" style="text-align:center;padding:2rem">No accounts match.</td></tr>';
    };

    EF.toggleUser = async function (id, active) {
        const u = (EF.userList || []).find((x) => x.id === id);
        if (!active && !confirm('Deactivate ' + (u ? u.name : 'this account') + '? They will not be able to sign in.')) return;
        try {
            await EF.api('/api/admin/users/' + id + '/active', { method: 'POST', body: { active } });
            EF.toast(active ? 'Account activated.' : 'Account deactivated.', 'success');
            EF.loadUsers();
        } catch (e) { EF.toast(e.message, 'error'); }
    };

    // ------------------------------------------------------------------ event approvals (admin)
    EF.setApprovalCount = function (n) {
        const el = $('navApprovalCount');
        if (!el) return;
        el.textContent = n;
        el.classList.toggle('hidden', !n);
    };
    EF.refreshApprovalCount = async function () {
        if (EF.role() !== 'ADMIN') return;
        try { EF.setApprovalCount((await EF.api('/api/admin/stats')).pendingApprovals); } catch (e) { /* badge is optional */ }
    };

    EF.loadApprovals = async function () {
        const host = $('approvalsContent');
        setLoading(host);
        try {
            const events = await fetchAllEvents();
            const pending = events.filter((e) => e.status === 'Pending Approval');
            const rejected = events.filter((e) => e.status === 'Rejected');
            EF.setApprovalCount(pending.length);
            const row = (e, actions) => '<tr><td><strong>' + esc(e.name) + '</strong></td><td>' + esc(e.organizerName || '-') + '</td><td>' + esc(e.type || '-') + '</td><td>' + esc(e.department || 'All departments') + '</td><td>' + formatDate(e.eventDate) +
                (e.eventTime ? '<div class="ef-muted" style="font-size:.75rem">' + esc(e.eventTime) + (e.endTime ? ' - ' + esc(e.endTime) : '') + '</div>' : '') + '</td><td>' + esc(e.location) + '</td><td>' + (e.capacity || '-') + '</td><td><div style="display:flex;gap:.5rem;flex-wrap:wrap">' + actions + '</div></td></tr>';
            const head = '<thead><tr><th>Event</th><th>Organizer</th><th>Category</th><th>Department</th><th>Date</th><th>Venue</th><th>Capacity</th><th>Actions</th></tr></thead>';
            const view = (e) => '<button class="btn btn-outline btn-sm" onclick="viewEvent(' + e.id + ')">Details</button>';
            host.innerHTML = '<div class="card"><div class="card-header"><h3 class="card-title">Pending approval (' + pending.length + ')</h3></div>' +
                (pending.length ? '<div class="ef-table-wrap"><table>' + head + '<tbody>' + pending.map((e) => row(e, view(e) +
                    '<button class="btn btn-primary btn-sm" onclick="EF.decide(' + e.id + ',\'approve\')"><i class="fas fa-check"></i> Approve</button>' +
                    '<button class="btn ef-btn-danger btn-sm" onclick="EF.decide(' + e.id + ',\'reject\')"><i class="fas fa-xmark"></i> Reject</button>')).join('') + '</tbody></table></div>' :
                    '<div class="ef-state"><i class="fas fa-circle-check"></i><h3>Nothing to review</h3><p>Events submitted by organizers appear here for approval.</p></div>') + '</div>' +
                (rejected.length ? '<div class="card" style="margin-top:1.5rem"><div class="card-header"><h3 class="card-title">Rejected (' + rejected.length + ')</h3></div><div class="ef-table-wrap"><table>' + head + '<tbody>' + rejected.map((e) => row(e, view(e))).join('') + '</tbody></table></div><p class="ef-muted" style="padding:0 1rem 1rem">Organizers can edit a rejected event and resubmit it for approval.</p></div>' : '');
        } catch (e) {
            setError(host, e.message, 'EF.loadApprovals()');
        }
    };

    EF.decide = async function (id, action) {
        const ev = (allEvents || []).find((e) => e.id === id);
        if (action === 'reject' && !confirm('Reject "' + (ev ? ev.name : 'this event') + '"? The organizer can edit and resubmit it.')) return;
        try {
            await EF.api('/api/admin/events/' + id + '/' + action, { method: 'POST' });
            EF.toast(action === 'approve' ? 'Event approved and published to students.' : 'Event rejected.', 'success');
            closeModal('eventDetailModal');
            await fetchAllEvents();
            if ($('approvals').classList.contains('active')) EF.loadApprovals();
            else if ($('events').classList.contains('active')) loadAllEvents();
            else loadDashboard();
            EF.refreshApprovalCount();
        } catch (e) { EF.toast(e.message, 'error'); }
    };

    // Organizers see how many of their own events are still waiting for or were refused by the admin.
    EF.organizerPending = function () {
        const mine = (allEvents || []).filter((e) => EF.canManage(e));
        const pending = mine.filter((e) => e.status === 'Pending Approval').length;
        const rejected = mine.filter((e) => e.status === 'Rejected').length;
        const host = $('adminOverview');
        if (!host || (!pending && !rejected)) return;
        host.innerHTML = '<div class="ef-banner"><i class="fas fa-clipboard-check"></i><div>' + (pending ? '<strong>' + pending + ' event' + (pending > 1 ? 's' : '') + ' awaiting admin approval.</strong> ' : '') +
            (rejected ? '<strong>' + rejected + ' rejected.</strong> Edit a rejected event to resubmit it.' : '') + '</div><button class="btn btn-outline btn-sm" onclick="showPage(\'events\')">View events</button></div>';
    };

    // ------------------------------------------------------------------ profile (all roles)
    EF.loadProfile = async function () {
        const host = $('profileContent');
        const u = EF.user;
        const student = EF.isParticipant();
        $('profileSubtitle').textContent = student ? 'Your student details are used for event registrations and certificates.' : 'Your account details.';
        const deptOpts = EF.meta.departments.map((d) => '<option value="' + esc(d) + '"' + (d === u.department ? ' selected' : '') + '>' + esc(d) + '</option>').join('');
        const yearOpts = EF.meta.years.map((y) => '<option value="' + y + '"' + (y === u.yearOfStudy ? ' selected' : '') + '>Year ' + y + '</option>').join('');
        host.innerHTML = '<div class="card"><form id="profileForm"><div class="form-grid">' +
            '<div class="form-group"><label class="form-label">Full name</label><input class="form-input" name="name" required maxlength="120" value="' + esc(u.name) + '"></div>' +
            '<div class="form-group"><label class="form-label">Email</label><input class="form-input" value="' + esc(u.email) + '" disabled></div>' +
            '<div class="form-group"><label class="form-label">Role</label><input class="form-input" value="' + esc(EF.roleLabel(u.role)) + '" disabled></div>' +
            (student ? '<div class="form-group"><label class="form-label">USN / Registration number</label><input class="form-input" name="usn" required maxlength="20" pattern="[A-Za-z0-9]{5,20}" title="5 to 20 letters or digits" style="text-transform:uppercase" value="' + esc(u.usn || '') + '"></div>' +
                '<div class="form-group"><label class="form-label">Department</label><select class="form-select" name="department" required><option value="">Select department...</option>' + deptOpts + '</select></div>' +
                '<div class="form-group"><label class="form-label">Year of study</label><select class="form-select" name="yearOfStudy" required><option value="">Select year...</option>' + yearOpts + '</select></div>' : '') +
            '</div><div style="margin-top:1.5rem"><button class="btn btn-primary" type="submit"><i class="fas fa-save"></i> Save profile</button></div></form></div>';
        $('profileForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const f = e.target;
            const body = { name: f.name.value };
            if (student) {
                body.usn = f.usn.value.trim().toUpperCase();
                body.department = f.department.value;
                body.yearOfStudy = parseInt(f.yearOfStudy.value, 10);
            }
            try {
                EF.user = await EF.api('/api/auth/profile', { method: 'PUT', body });
                applyRole();
                EF.toast('Profile saved.', 'success');
            } catch (err) { EF.toast(err.message, 'error'); }
        });
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
                host.innerHTML = html + '<div class="card"><div class="ef-state"><i class="fas fa-chart-column"></i><h3>No registrations yet</h3><p>Charts appear once students register for your events.</p></div></div>';
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
