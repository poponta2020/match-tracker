(function () {
  if (window.__VRP_INJECTED__) {
    return;
  }
  window.__VRP_INJECTED__ = true;

  var TOKEN = "{{token}}";
  var BASE_URL = "{{baseUrl}}";
  var PROXY_PREFIX = "{{proxyPrefix}}";
  // 会場サイト上での「現在ページ」絶対 URL。HTML を /view または /fetch から返した時点で
  // サーバ側が決定する。フロント JS が動的に生成する相対 URL は、ブラウザ既定の
  // window.location ではなく、ここで埋め込まれた会場側 URL を基準に解決する必要がある。
  // (proxy 配下では window.location.pathname がプロキシ URL になり、相対パス解決が破綻するため)
  var CURRENT_UPSTREAM_URL = "{{currentUpstreamUrl}}";

  var venueOrigin;
  try {
    venueOrigin = new URL(BASE_URL).origin;
  } catch (e) {
    venueOrigin = BASE_URL;
  }

  function isAlreadyProxied(url) {
    if (!url) return false;
    return url.indexOf(PROXY_PREFIX) === 0
      || url.indexOf(window.location.origin + PROXY_PREFIX) === 0;
  }

  function appendToken(urlStr) {
    try {
      var u = new URL(urlStr, window.location.href);
      u.searchParams.delete('token');
      u.searchParams.set('token', TOKEN);
      // 入力が絶対 URL なら絶対 URL のまま、相対なら path+query+fragment で返す。
      if (/^https?:/i.test(urlStr)) {
        return u.toString();
      }
      return u.pathname + u.search + u.hash;
    } catch (e) {
      var sep = urlStr.indexOf('?') >= 0 ? '&' : '?';
      return urlStr + sep + 'token=' + encodeURIComponent(TOKEN);
    }
  }

  function rewriteUrl(input) {
    if (!input) return input;
    if (typeof input !== 'string') {
      try { input = String(input); } catch (e) { return input; }
    }

    var lower = input.toLowerCase();
    if (lower.indexOf('javascript:') === 0
        || lower.indexOf('mailto:') === 0
        || lower.indexOf('data:') === 0
        || lower.indexOf('about:') === 0
        || lower.indexOf('blob:') === 0
        || input.charAt(0) === '#') {
      return input;
    }

    if (isAlreadyProxied(input)) {
      return appendToken(input);
    }

    // 相対 URL は会場サイト上での「現在ページ」を基準に解決する。
    // 絶対 URL ならそのまま new URL で扱える。
    var resolved;
    try {
      var base = CURRENT_UPSTREAM_URL || window.location.href;
      resolved = new URL(input, base);
    } catch (e) {
      return input;
    }

    if (resolved.protocol !== 'http:' && resolved.protocol !== 'https:') {
      return input;
    }

    if (resolved.origin !== venueOrigin) {
      return input; // 会場外 URL は透過
    }

    var pathAndQuery = resolved.pathname + resolved.search + resolved.hash;
    return appendToken(PROXY_PREFIX + pathAndQuery);
  }

  var origAssign = window.Location && window.Location.prototype && window.Location.prototype.assign;
  if (origAssign) {
    window.Location.prototype.assign = function (url) {
      return origAssign.call(this, rewriteUrl(url));
    };
  }

  var origReplace = window.Location && window.Location.prototype && window.Location.prototype.replace;
  if (origReplace) {
    window.Location.prototype.replace = function (url) {
      return origReplace.call(this, rewriteUrl(url));
    };
  }

  var origOpen = window.open;
  if (typeof origOpen === "function") {
    window.open = function (url, name, features) {
      return origOpen.call(this, rewriteUrl(url), name, features);
    };
  }

  if (typeof window.fetch === "function") {
    var origFetch = window.fetch;
    window.fetch = function (input, init) {
      try {
        if (typeof input === "string") {
          input = rewriteUrl(input);
        } else if (input && typeof input.url === "string") {
          var rewritten = rewriteUrl(input.url);
          if (rewritten !== input.url) {
            input = new Request(rewritten, input);
          }
        }
      } catch (e) {
        // fall through to original fetch
      }
      var p = origFetch.call(this, input, init);
      return p.then(function (res) {
        try {
          if (res && res.headers && res.headers.get && res.headers.get("X-VRP-Completed") === "true") {
            window.dispatchEvent(new CustomEvent("vrp-reservation-completed"));
          }
        } catch (e) {}
        return res;
      });
    };
  }

  var XHR = window.XMLHttpRequest;
  if (XHR && XHR.prototype) {
    var origXhrOpen = XHR.prototype.open;
    XHR.prototype.open = function (method, url) {
      var args = Array.prototype.slice.call(arguments);
      args[1] = rewriteUrl(url);
      this.addEventListener("readystatechange", function () {
        try {
          if (this.readyState === 4
              && this.getResponseHeader
              && this.getResponseHeader("X-VRP-Completed") === "true") {
            window.dispatchEvent(new CustomEvent("vrp-reservation-completed"));
          }
        } catch (e) {}
      });
      return origXhrOpen.apply(this, args);
    };
  }

  // 会場固有の追加フック (空でもよい)
  try {
    /* {{venueInjectScript}} */
  } catch (e) {
    // venue-specific hook errors must not break the page
    if (window.console && console.warn) {
      console.warn("[vrp] venue-specific inject script failed:", e);
    }
  }
})();
