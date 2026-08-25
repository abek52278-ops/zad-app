function renderProfile(t, ar) {
  var name = ar?'سارة العتيبي':'Sarah Al-Otaibi';
  var initial = ar?'س':'S';
  var kidsOn = state.kidsMode;
  var menuHtml = PROFILE_MENU.map(function(p){
    return '<div data-act="'+(p.go?'goTo':'noop')+'" data-arg="'+(p.go||'')+'" style="padding:14px 16px;font-size:14px;font-weight:600;color:#0F172A;border-bottom:1px solid rgba(0,0,0,.05);cursor:pointer;">'+p[state.lang]+'</div>';
  }).join('');
  return '<div style="padding:18px 20px 130px;display:flex;flex-direction:column;gap:14px;animation:zadFadeUp .4s ease both;">'
    + '<div style="background:linear-gradient(135deg,#064E3B,#0B6B4E);border-radius:22px;padding:20px;color:#fff;display:flex;flex-direction:column;align-items:center;gap:8px;">'
    + '<div style="width:64px;height:64px;border-radius:50%;background:rgba(255,255,255,.15);border:2px solid rgba(255,255,255,.4);display:flex;align-items:center;justify-content:center;font-size:22px;font-weight:700;">'+initial+'</div>'
    + '<span style="font-size:16px;font-weight:700;">'+name+'</span><span style="font-size:11.5px;opacity:.7;">#ZAD-2481</span></div>'
    + '<div style="background:#fff;border-radius:18px;padding:4px;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);"><div style="display:flex;justify-content:space-between;align-items:center;padding:14px 16px;"><div style="display:flex;flex-direction:column;gap:2px;"><span style="font-size:14px;font-weight:600;color:#0F172A;">'+t.kidsToggleLabel+'</span><span style="font-size:11.5px;color:#9CA3AF;">'+t.kidsToggleSub+'</span></div>'
    + '<div data-act="toggleKidsMode" style="width:44px;height:26px;border-radius:9999px;background:'+(kidsOn?'#7C3AED':'#E5E7EB')+';position:relative;cursor:pointer;transition:background .2s;flex-shrink:0;"><div style="width:20px;height:20px;border-radius:50%;background:#fff;position:absolute;top:3px;'+(ar?'right':'left')+':'+(kidsOn?21:3)+'px;transition:all .2s;box-shadow:0 1px 3px rgba(0,0,0,.2);"></div></div></div></div>'
    + '<div style="background:#fff;border-radius:18px;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);overflow:hidden;">'+menuHtml+'</div></div>';
}