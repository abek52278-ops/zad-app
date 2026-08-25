function renderBudget(t, ar) {
  var statusLabel = {paid:ar?'مدفوع':'Paid',pending:ar?'مستحق':'Pending',scheduled:ar?'مجدول':'Scheduled'};
  var statusColor = {paid:'#064E3B',pending:'#DC5B4B',scheduled:'#B45309'};
  var oblHtml = OBLIGATIONS.map(function(o){
    return '<div style="background:#fff;border-radius:18px;padding:16px;display:flex;flex-direction:column;gap:10px;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);">'
      + '<div style="display:flex;justify-content:space-between;align-items:center;"><span style="font-size:15px;font-weight:600;color:#0F172A;">'+o[state.lang].name+'</span><span style="font-size:15px;font-weight:700;color:#0F172A;">'+(ar?o.amountAr:o.amountEn)+'</span></div>'
      + '<div style="display:flex;justify-content:space-between;align-items:center;"><span style="font-size:12.5px;color:#9CA3AF;">'+o[state.lang].due+'</span><span style="font-size:11px;font-weight:700;border-radius:9999px;padding:3px 9px;background:rgba(6,78,59,.06);color:'+statusColor[o.statusKind]+';">'+statusLabel[o.statusKind]+'</span></div>'
      + '<div style="height:6px;border-radius:99px;background:#F1F4F3;overflow:hidden;"><div style="width:'+o.pct+'%;height:100%;border-radius:99px;background:'+statusColor[o.statusKind]+';"></div></div></div>';
  }).join('');
  return '<div style="padding:18px 20px 130px;display:flex;flex-direction:column;gap:14px;animation:zadFadeUp .4s ease both;">'
    + '<div data-act="openSheet" style="background:#064E3B;border-radius:22px;padding:20px;color:#fff;cursor:pointer;display:flex;flex-direction:column;gap:6px;"><span style="font-size:12.5px;font-weight:600;color:rgba(255,255,255,.7);">'+t.available+'</span><span style="font-size:34px;font-weight:700;">'+(ar?'≈ 3,240 ر.س':'≈ SAR 3,240')+'</span></div>'
    + '<div style="background:#fff;border-radius:18px;padding:16px 18px;display:flex;justify-content:space-between;align-items:center;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);"><span style="font-size:13px;font-weight:600;color:#6B7280;">'+t.totalObligations+'</span><span style="font-size:19px;font-weight:700;color:#B45309;">'+(ar?'3,790 ر.س':'SAR 3,790')+'</span></div>'
    + oblHtml + '</div>';
}