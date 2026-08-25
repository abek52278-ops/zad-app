function renderSubs(t, ar) {
  var subsHtml = SUBS_ITEMS.map(function(s){
    return '<div style="background:#fff;border-radius:16px;padding:14px 16px;display:flex;justify-content:space-between;align-items:center;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);border-'+(ar?'right':'left')+':3px solid '+s.color+';"><div style="display:flex;flex-direction:column;gap:4px;"><span style="font-size:14px;font-weight:600;color:#0F172A;">'+s[state.lang]+'</span><span style="font-size:11.5px;color:#9CA3AF;">'+(ar?s.renewAr:s.renewEn)+'</span></div><span style="font-size:14px;font-weight:700;color:#0F172A;">'+(ar?s.priceAr:s.priceEn)+'</span></div>';
  }).join('');
  return '<div style="padding:18px 20px 130px;display:flex;flex-direction:column;gap:14px;animation:zadFadeUp .4s ease both;">'
    + '<div style="background:linear-gradient(135deg,#064E3B,#0B6B4E);border-radius:16px;padding:14px 16px;color:#fff;font-size:13px;font-weight:600;">'+(ar?'إجمالي الاشتراكات الشهرية: 294 ر.س':'Total monthly subscriptions: SAR 294')+'</div>'
    + subsHtml + '</div>';
}