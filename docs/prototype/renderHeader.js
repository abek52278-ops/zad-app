function renderHeader(t, ar) {
  var kidsBadge = state.kidsMode ? '<span style="font-size:11px;font-weight:700;color:#7C3AED;background:rgba(124,58,237,.1);border-radius:9999px;padding:3px 9px;">'+t.kidsBadge+'</span>' : '';
  var exitBtn = state.kidsMode ? '<button data-act="exitKidsMode" style="border:none;background:rgba(124,58,237,.1);color:#7C3AED;font-size:11.5px;font-weight:700;border-radius:9999px;padding:6px 12px;cursor:pointer;">'+t.exitKids+'</button>' : '';
  var screen = (state.kidsMode && state.screen !== 'family') ? 'home' : state.screen;
  return '<div style="position:sticky;top:0;z-index:5;background:rgba(249,250,251,.92);backdrop-filter:blur(14px);padding:54px 20px 14px;display:flex;flex-direction:column;gap:12px;border-bottom:1px solid rgba(6,78,59,.06);">'
    + '<div style="display:flex;align-items:center;justify-content:space-between;">'
    + '<div style="display:flex;align-items:center;gap:10px;">'
    + '<button data-act="openDrawer" style="border:none;background:rgba(6,78,59,.08);border-radius:10px;width:32px;height:32px;display:flex;align-items:center;justify-content:center;cursor:pointer;"><svg width="16" height="12" viewBox="0 0 16 12" fill="none" stroke="#064E3B" stroke-width="1.8" stroke-linecap="round"><line x1="0" y1="1" x2="16" y2="1"/><line x1="0" y1="6" x2="16" y2="6"/><line x1="0" y1="11" x2="16" y2="11"/></svg></button>'
    + '<div style="width:32px;height:32px;border-radius:9px;background:#E6F4EC;display:flex;align-items:center;justify-content:center;">'+CARROT_SVG_SM+'</div>'
    + '<span style="font-weight:700;font-size:19px;color:#064E3B;letter-spacing:-.2px;">'+t.appName+'</span>'+kidsBadge
    + '</div>'
    + '<div style="display:flex;gap:8px;align-items:center;">'+exitBtn+'<button data-act="toggleLang" style="border:none;background:rgba(6,78,59,.08);border-radius:9999px;padding:7px 14px;font-size:13px;font-weight:600;color:#064E3B;cursor:pointer;">'+t.langToggle+'</button></div>'
    + '</div>'
    + '<div style="font-size:26px;font-weight:700;color:#0F172A;letter-spacing:-.3px;">'+(t.titles[screen]||t.titles.home)+'</div>'
    + '</div>';
}