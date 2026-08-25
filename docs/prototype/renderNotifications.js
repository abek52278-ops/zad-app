function renderNotifications(t, ar) {
  var notifKindColor = {info:'#064E3B',warn:'#B45309',danger:'#DC5B4B'};
  var itemsHtml = NOTIFS.map(function(n){
    return '<div style="background:#fff;border-radius:16px;padding:14px 16px;display:flex;gap:12px;align-items:flex-start;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);"><div style="width:10px;height:10px;border-radius:50%;background:'+notifKindColor[n.kind]+';margin-top:5px;flex-shrink:0;"></div><div style="flex:1;display:flex;flex-direction:column;gap:3px;"><span style="font-size:14px;font-weight:600;color:#0F172A;">'+n[state.lang].title+'</span><span style="font-size:12.5px;color:#6B7280;">'+n[state.lang].body+'</span></div></div>';
  }).join('');
  return '<div style="padding:18px 20px 130px;display:flex;flex-direction:column;gap:12px;animation:zadFadeUp .4s ease both;">'+itemsHtml+'</div>';
}