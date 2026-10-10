/*
 * Vidsource video link extractor (v2).
 *
 * Modern, low-overhead approach:
 *  - PerformanceObserver (Resource Timing) sees every network request,
 *    including fetch/XHR/worker/media loads — no per-call wrapping cost.
 *  - fetch/XHR are still hooked so URLs are known before the bytes move.
 *  - HTMLMediaElement src/play hooks catch players that build URLs in JS.
 *  - The DOM watch is deliberately light (childList on <body> only) so heavy
 *    pages do not slow down like with a full-subtree observer.
 */
(function () {
    if (window.__vidsourceHooked) return;
    window.__vidsourceHooked = true;

    var seen = Object.create(null);

    function looksLikeMedia(url) {
        return typeof url === 'string' &&
            /\.(m3u8|mpd|mp4|m4v|webm|mkv|avi|mov|flv|m3u|ts|mp3|aac|m4a)(\?|#|$)/i.test(url);
    }

    function send(url, title) {
        if (!url || typeof url !== 'string') return;
        if (url.indexOf('blob:') === 0 || url.indexOf('data:') === 0) return;
        if (url.indexOf('http') !== 0) return;
        if (seen[url]) return;
        seen[url] = 1;
        try {
            if (window.Vidsource && window.Vidsource.onVideoUrl) {
                window.Vidsource.onVideoUrl(url, title || document.title || '');
            }
        } catch (e) { }
    }

    function check(url) {
        if (looksLikeMedia(url)) send(url, document.title);
    }

    // ---- 1) Resource Timing: catch every network load (modern browsers) ----
    try {
        if (window.PerformanceObserver &&
            PerformanceObserver.supportedEntryTypes &&
            PerformanceObserver.supportedEntryTypes.indexOf('resource') !== -1) {
            new PerformanceObserver(function (list) {
                var entries = list.getEntries();
                for (var i = 0; i < entries.length; i++) check(entries[i].name);
            }).observe({ entryTypes: ['resource'] });
        }
    } catch (e) { }

    // ---- 2) fetch / XHR: know the URL the moment it is requested ----
    try {
        var originalFetch = window.fetch;
        if (originalFetch) {
            window.fetch = function (input) {
                try {
                    check(typeof input === 'string' ? input : (input && input.url));
                } catch (e) { }
                return originalFetch.apply(this, arguments);
            };
        }
    } catch (e) { }

    try {
        var originalOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function (method, url) {
            try { check(url); } catch (e) { }
            return originalOpen.apply(this, arguments);
        };
    } catch (e) { }

    // ---- 3) Media elements ----
    try {
        var originalPlay = HTMLMediaElement.prototype.play;
        HTMLMediaElement.prototype.play = function () {
            try { send(this.currentSrc || this.src, document.title); } catch (e) { }
            return originalPlay.apply(this, arguments);
        };
    } catch (e) { }

    try {
        var srcDesc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
        if (srcDesc && srcDesc.set) {
            Object.defineProperty(HTMLMediaElement.prototype, 'src', {
                configurable: true,
                get: srcDesc.get,
                set: function (value) {
                    try { check(value); } catch (e) { }
                    return srcDesc.set.call(this, value);
                }
            });
        }
    } catch (e) { }

    document.addEventListener('play', function (ev) {
        var el = ev.target;
        if (el) send(el.currentSrc || el.src, document.title);
    }, true);

    // ---- 4) Light DOM watch: new <video> tags and <source> children ----
    function scanMedia() {
        try {
            var media = document.querySelectorAll('video, audio');
            for (var i = 0; i < media.length; i++) {
                var el = media[i];
                var src = el.currentSrc || el.src;
                if (src) { send(src, document.title); continue; }
                var sources = el.querySelectorAll('source');
                for (var j = 0; j < sources.length; j++) check(sources[j].src);
            }
        } catch (e) { }
    }

    function idleScan() {
        if (window.requestIdleCallback) {
            requestIdleCallback(scanMedia, { timeout: 2000 });
        } else {
            setTimeout(scanMedia, 300);
        }
    }

    try {
        if (document.body) {
            new MutationObserver(idleScan).observe(document.body, { childList: true });
        }
    } catch (e) { }
    setInterval(idleScan, 6000);
    idleScan();
})();
