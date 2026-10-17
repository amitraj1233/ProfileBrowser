package com.example.profilebrowser

import org.json.JSONObject

object FingerprintJs {
    fun build(p: Profile): String {
        val pr = PRESETS.getValue(p.os)
        val cfg = JSONObject()
            .put("platform", pr.platform).put("uaPlatform", pr.uaPlatform)
            .put("platformVersion", pr.platformVersion).put("ua", pr.ua)
            .put("cores", p.cores).put("memory", p.memory)
            .put("vendor", p.vendor).put("renderer", p.renderer)
            .put("seed", p.seed).put("deviceId", p.deviceId).put("chromeVer", CHROME_VER)
        val base = TEMPLATE.replace("__CFG__", cfg.toString())
        val custom = if (p.script.isBlank()) "" else "\ntry {\n" + p.script + "\n} catch (e) {}\n"
        return base + custom
    }

    private const val TEMPLATE = """
(function () {
  var CFG = __CFG__;
  var s = CFG.seed >>> 0;
  function rnd() { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }
  function def(obj, prop, val) {
    try { Object.defineProperty(obj, prop, { get: function () { return val; }, configurable: true }); } catch (e) {}
  }
  var N = Navigator.prototype;
  def(N, "platform", CFG.platform);
  def(N, "userAgent", CFG.ua);
  def(N, "appVersion", CFG.ua.replace("Mozilla/", ""));
  def(N, "hardwareConcurrency", CFG.cores);
  def(N, "deviceMemory", CFG.memory);
  def(N, "maxTouchPoints", 0);
  def(N, "webdriver", false);

  var brands = [
    { brand: "Chromium", version: CFG.chromeVer },
    { brand: "Google Chrome", version: CFG.chromeVer },
    { brand: "Not_A Brand", version: "24" }
  ];
  var uad = {
    brands: brands, mobile: false, platform: CFG.uaPlatform,
    getHighEntropyValues: function () {
      return Promise.resolve({
        brands: brands, mobile: false, platform: CFG.uaPlatform,
        platformVersion: CFG.platformVersion, architecture: "x86", bitness: "64",
        model: "", uaFullVersion: CFG.chromeVer + ".0.0.0"
      });
    },
    toJSON: function () { return { brands: brands, mobile: false, platform: CFG.uaPlatform }; }
  };
  def(N, "userAgentData", uad);

  function patchGL(proto) {
    if (!proto) return;
    var orig = proto.getParameter;
    proto.getParameter = function (p) {
      if (p === 37445) return CFG.vendor;
      if (p === 37446) return CFG.renderer;
      return orig.call(this, p);
    };
  }
  if (window.WebGLRenderingContext) patchGL(WebGLRenderingContext.prototype);
  if (window.WebGL2RenderingContext) patchGL(WebGL2RenderingContext.prototype);

  function noisy(canvas) {
    var w = canvas.width, h = canvas.height;
    if (!w || !h) return canvas;
    var c = document.createElement("canvas");
    c.width = w; c.height = h;
    var ctx = c.getContext("2d");
    ctx.drawImage(canvas, 0, 0);
    try {
      var img = ctx.getImageData(0, 0, w, h);
      for (var i = 0; i < 20; i++) {
        var idx = Math.floor(rnd() * (img.data.length / 4)) * 4;
        img.data[idx] = (img.data[idx] + 1) & 255;
      }
      ctx.putImageData(img, 0, 0);
    } catch (e) {}
    return c;
  }
  var origURL = HTMLCanvasElement.prototype.toDataURL;
  HTMLCanvasElement.prototype.toDataURL = function () { return origURL.apply(noisy(this), arguments); };
  var origBlob = HTMLCanvasElement.prototype.toBlob;
  HTMLCanvasElement.prototype.toBlob = function () { return origBlob.apply(noisy(this), arguments); };

  if (window.AudioBuffer) {
    var origGCD = AudioBuffer.prototype.getChannelData;
    AudioBuffer.prototype.getChannelData = function (ch) {
      var d = origGCD.call(this, ch);
      for (var i = 0; i < d.length; i += 100) d[i] += (rnd() - 0.5) * 1e-7;
      return d;
    };
  }

  if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
    var origEnum = navigator.mediaDevices.enumerateDevices.bind(navigator.mediaDevices);
    var base = CFG.deviceId.replace(/-/g, "");
    navigator.mediaDevices.enumerateDevices = function () {
      return origEnum().then(function (list) {
        return list.map(function (d, i) {
          return { deviceId: base.slice(0, 24) + i, groupId: base.slice(8, 32), kind: d.kind, label: d.label };
        });
      });
    };
  }
})();
"""
}
