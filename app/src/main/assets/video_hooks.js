/*
 * Vidsource video URL extractor.
 * Injected into every loaded page. Watches HTML5 media elements, players and
 * network requests, then forwards direct media URLs to the native player.
 */
(function () {
    if (window.__vidsourceHooked) return;
    window.__vidsourceHooked = true;

    function send(url, title) {
        if (!url || typeof url !== 'string') return;
        if (url.indexOf('blob:') === 0 || url.indexOf('data:') === 0) return;
        if (url.indexOf('http') !== 0) return;
        try {
            if (window.Vidsource && window.Vidsource.onVideoUrl) {
                window.Vidsource.onVideoUrl(url, title || document.title || '');
            }
        } catch (e) { }
    }

    function looksLikeMedia(url) {
        return /\.(m3u8|mpd|mp4|m4v|webm|mkv|avi|mov|flv|ts|mp3|aac|m4a)(\?|#|$)/i.test(url);
    }

    function check(url) {
        if (url && looksLikeMedia(url)) send(url, document.title);
    }

    // 1) HTML5 media elements.
    function scanMedia() {
        var media = document.querySelectorAll('video, audio');
        for (var i = 0; i < media.length; i++) {
            var el = media[i];
            var src = el.currentSrc || el.src;
            if (src) {
                send(src, document.title);
            } else {
                var sources = el.querySelectorAll('source');
                for (var j = 0; j < sources.length; j++) {
                    if (sources[j].src) send(sources[j].src, document.title);
                }
            }
        }
    }

    // 2) Hook play() so anything the site starts playing is reported.
    var originalPlay = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function () {
        try {
            var src = this.currentSrc || this.src;
            if (src) send(src, document.title);
        } catch (e) { }
        return originalPlay.apply(this, arguments);
    };

    // 3) Hook the src property.
    try {
        var srcDesc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
        if (srcDesc && srcDesc.set) {
            Object.defineProperty(HTMLMediaElement.prototype, 'src', {
                configurable: true,
                get: srcDesc.get,
                set: function (value) {
                    try { send(value, document.title); } catch (e) { }
                    return srcDesc.set.call(this, value);
                }
            });
        }
    } catch (e) { }

    // 4) Capture media URLs from fetch / XHR (hls.js, dash.js, custom players).
    try {
        var originalFetch = window.fetch;
        if (originalFetch) {
            window.fetch = function (input) {
                try {
                    var url = (typeof input === 'string') ? input : (input && input.url);
                    check(url);
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

    // 5) play events (capture phase, catches everything).
    document.addEventListener('play', function (ev) {
        var el = ev.target;
        if (el && (el.currentSrc || el.src)) send(el.currentSrc || el.src, document.title);
    }, true);

    // 6) Keep scanning as the DOM changes (SPA pages).
    try {
        var observer = new MutationObserver(function () {
            scanMedia();
        });
        observer.observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) { }
    setInterval(scanMedia, 2500);
    scanMedia();
})();
