function renderPharmacy(t, ar) {
  var pharmKindLabel = {ok:ar?'منتظم':'On track',low:ar?'كمية منخفضة':'Low stock',confirm:ar?'يحتاج تأكيد':'Needs confirm'};
  var pharmKindColor = {ok:'#064E3B',low:'#B45309',confirm:'#DC5B4B'};
  var itemsHtml = PHARM_ITEMS.map(function(p){
    return '<div style="background:#fff;border-radius:16px;padding:14px 16px;display:flex;flex-direction:column;gap:8px;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);"><div style="display:flex;justify-content:space-between;"><span style="font-size:14px;font-weight:600;color:#0F172A;">'+p[state.lang]+'</span><span style="font-size:11px;font-weight:700;border-radius:9999px;padding:4px 10px;background:rgba(6,78,59,.06);color:'+pharmKindColor[p.kind]+';">'+pharmKindLabel[p.kind]+'</span></div><span style="font-size:12px;color:#9CA3AF;">'+(ar?p.memberAr:p.memberEn)+' · '+(ar?p.doseAr:p.doseEn)+'</span></div>';
  }).join('');
  return '<div style="padding:18px 20px 130px;display:flex;flex-direction:column;gap:14px;animation:zadFadeUp .4s ease both;">'
    + '<div style="display:flex;gap:10px;">'+card('<span style="font-size:11px;color:#9CA3AF;font-weight:600;">'+t.adherence+'</span><div style="font-size:20px;font-weight:700;color:#064E3B;">91%</div>','flex:1;')+card('<span style="font-size:11px;color:#9CA3AF;font-weight:600;">'+t.monthlyCost+'</span><div style="font-size:20px;font-weight:700;color:#0F172A;">'+(ar?'180 ر.س':'SAR 180')+'</div>','flex:1;')+'</div>'
    + itemsHtml + '</div>';
}