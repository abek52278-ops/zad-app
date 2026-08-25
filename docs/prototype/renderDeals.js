function renderDeals(t, ar) {
  var storesHtml = DEALS_STORES.map(function(d){
    return '<div style="background:#fff;border-radius:16px;padding:14px 16px;display:flex;justify-content:space-between;align-items:center;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);"><div style="display:flex;flex-direction:column;gap:4px;"><span style="font-size:14px;font-weight:600;color:#0F172A;">'+d[state.lang]+'</span><span style="font-size:11.5px;color:#9CA3AF;">'+(ar?d.typeAr:d.typeEn)+'</span></div><span style="font-size:12.5px;font-weight:600;color:#064E3B;">'+d.dist+'</span></div>';
  }).join('');
  return '<div style="padding:18px 20px 130px;display:flex;flex-direction:column;gap:14px;animation:zadFadeUp .4s ease both;"><div style="background:rgba(6,78,59,.06);border-radius:14px;padding:12px 14px;font-size:12px;color:#064E3B;line-height:1.5;">'+t.dealsDisclaimer+'</div>'+storesHtml+'</div>';
}