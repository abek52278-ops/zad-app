function renderMaintenance(t, ar) {
  var maintKindColor = {ok:'#064E3B',soon:'#B45309',over:'#DC5B4B'};
  var itemsHtml = MAINT_ITEMS.map(function(m){
    return '<div style="background:#fff;border-radius:16px;padding:14px 16px;display:flex;justify-content:space-between;align-items:center;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);"><div style="display:flex;flex-direction:column;gap:4px;"><span style="font-size:14px;font-weight:600;color:#0F172A;">'+m[state.lang]+'</span><span style="font-size:11.5px;color:#9CA3AF;">'+(ar?m.warrantyAr:m.warrantyEn)+'</span></div><span style="font-size:11px;font-weight:700;border-radius:9999px;padding:4px 10px;background:rgba(6,78,59,.06);color:'+maintKindColor[m.kind]+';">'+(ar?m.dueAr:m.dueEn)+'</span></div>';
  }).join('');
  return '<div style="padding:18px 20px 130px;display:flex;flex-direction:column;gap:14px;animation:zadFadeUp .4s ease both;">'+itemsHtml+'</div>';
}