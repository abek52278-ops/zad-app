function renderShopping(t, ar) {
  var itemsHtml = SHOP_ITEMS.map(function(si){
    var label = si.p==='high'?(ar?'حرج':'Critical'):si.p==='med'?(ar?'متوسط':'Medium'):(ar?'منخفض':'Low');
    var bg = si.p==='high'?'rgba(220,91,75,.12)':si.p==='med'?'rgba(180,83,9,.1)':'rgba(6,78,59,.08)';
    var color = si.p==='high'?'#DC5B4B':si.p==='med'?'#B45309':'#064E3B';
    return '<div style="background:#fff;border-radius:16px;padding:14px 16px;display:flex;justify-content:space-between;align-items:center;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);">'
      + '<div style="display:flex;flex-direction:column;gap:4px;"><span style="font-size:14px;font-weight:600;color:#0F172A;">'+si[state.lang]+'</span><span style="font-size:11.5px;color:#9CA3AF;">'+(ar?si.rangeAr:si.rangeEn)+'</span></div>'
      + '<span style="font-size:11px;font-weight:700;border-radius:9999px;padding:4px 10px;background:'+bg+';color:'+color+';">'+label+'</span></div>';
  }).join('');
  return '<div style="padding:18px 20px 130px;display:flex;flex-direction:column;gap:14px;animation:zadFadeUp .4s ease both;">'
    + '<div style="background:linear-gradient(135deg,#064E3B,#0B6B4E);border-radius:18px;padding:16px 18px;color:#fff;display:flex;flex-direction:column;gap:8px;"><span style="font-size:12.5px;opacity:.8;">'+t.shoppingTotal+'</span><span style="font-size:26px;font-weight:700;">'+(ar?'210 ر.س من أصل 300 ر.س':'SAR 210 of SAR 300')+'</span><button data-act="openSheet" style="align-self:flex-start;margin-top:4px;border:none;background:rgba(255,255,255,.16);color:#fff;font-size:12px;font-weight:700;border-radius:9999px;padding:7px 14px;cursor:pointer;">'+t.smartFill+'</button></div>'
    + itemsHtml + '</div>';
}