(function () {
  if (window.__VRP_INJECTED__) {
    return;
  }
  window.__VRP_INJECTED__ = true;

  var TOKEN = "{{token}}";
  var BASE_URL = "{{baseUrl}}";
  var PROXY_PREFIX = "{{proxyPrefix}}";

  function isVenueUrl(url) {
    if (!url) return false;
    if (url.indexOf(BASE_URL) === 0) return true;
    if (url.charAt(0) === "/" && url.indexOf("/" + PROXY_PREFIX.split("/").filter(Boolean)[0]) !== 0) {
      return true;
    }
    return false;
  }

  function isAlreadyProxied(url) {
    if (!url) return false;
    return url.indexOf(PROXY_PREFIX) === 0
      || url.indexOf(window.location.origin + PROXY_PREFIX) === 0;
  }

  function appendToken(url) {
    if (!url) return url;
    var sep = url.indexOf("?") >= 0 ? "&" : "?";
    return url + sep + "token=" + encodeURIComponent(TOKEN);
  }

  function rewriteUrl(url) {
    if (!url) return url;
    try {
      if (isAlreadyProxied(url)) return url;
      if (url.indexOf("javascript:") === 0
          || url.indexOf("mailto:") === 0
          || url.indexOf("data:") === 0
          || url.indexOf("about:") === 0
          || url.indexOf("blob:") === 0) {
        return url;
      }
      var path;
      if (url.indexOf(BASE_URL) === 0) {
        path = url.substring(BASE_URL.length);
        if (path.charAt(0) !== "/") path = "/" + path;
      } else if (url.charAt(0) === "/") {
        path = url;
      } else if (/^https?:\/\//i.test(url)) {
        return url;
      } else {
        var base = window.location.pathname || "/";
        var lastSlash = base.lastIndexOf("/");
        var dir = lastSlash >= 0 ? base.substring(0, lastSlash + 1) : "/";
        path = dir + url;
      }
      return appendToken(PROXY_PREFIX + path);
    } catch (e) {
      return url;
    }
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
