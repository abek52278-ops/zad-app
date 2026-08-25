function renderFamily(t, ar) {
  var tabs = [['chat',ar?'المحادثة':'Chat'],['tasks',ar?'المهام':'Tasks'],['members',ar?'الأعضاء':'Members']];
  var tabsHtml = tabs.map(function(tb){ return segBtn(tb[1], state.familyTab===tb[0], 'setFamilyTab', tb[0]); }).join('');
  var body = '';
  if (state.familyTab === 'chat') {
    body = '<div style="display:flex;flex-direction:column;gap:12px;">'+CHATS.map(function(m){
      var right = m.user;
      return '<div style="display:flex;justify-content:'+(right?'flex-end':'flex-start')+';"><div style="max-width:78%;padding:11px 15px;border-radius:16px;font-size:14px;line-height:1.45;background:'+(right?'#064E3B':'#fff')+';color:'+(right?'#fff':'#0F172A')+';box-shadow:'+(right?'none':'0 2px 8px rgba(15,23,42,.05)')+';">'+m[state.lang]+'</div></div>';
    }).join('')+'</div>';
  } else if (state.familyTab === 'members') {
    body = '<div style="display:flex;flex-direction:column;gap:10px;">'+FAMILY_MEMBERS.map(function(m){
      return '<div style="background:#fff;border-radius:16px;padding:14px 16px;display:flex;justify-content:space-between;align-items:center;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);"><div style="display:flex;flex-direction:column;gap:4px;"><span style="font-size:14px;font-weight:600;color:#0F172A;">'+m[state.lang]+'</span><span style="font-size:11.5px;color:#9CA3AF;">'+(ar?m.roleAr:m.roleEn)+'</span></div><span style="font-size:12px;font-weight:700;color:#064E3B;background:rgba(6,78,59,.08);border-radius:9999px;padding:4px 10px;">'+(ar?m.statAr:m.statEn)+'</span></div>';
    }).join('')+'</div>';
  } else {
    body = '<div style="display:flex;flex-direction:column;gap:10px;">'+FAMILY_TASKS.map(function(f){
      var bg = f.done?'rgba(6,78,59,.08)':'rgba(180,83,9,.1)';
      var color = f.done?'#064E3B':'#B45309';
      var status = f.done?(ar?'تم':'Done'):(ar?'قيد التنفيذ':'Pending');
      return '<div style="background:#fff;border-radius:16px;padding:13px 15px;display:flex;justify-content:space-between;align-items:center;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);"><div style="display:flex;flex-direction:column;gap:2px;"><span style="font-size:14px;font-weight:600;color:#0F172A;">'+f[state.lang]+'</span><span style="font-size:11.5px;color:#9CA3AF;">'+(ar?f.whoAr:f.whoEn)+'</span></div><span style="font-size:11px;font-weight:700;border-radius:9999px;padding:4px 10px;background:'+bg+';color:'+color+';">'+status+'</span></div>';
    }).join('')+'</div>';
  }
  return '<div style="padding:14px 20px 130px;display:flex;flex-direction:column;gap:14px;animation:zadFadeUp .4s ease both;"><div style="display:flex;gap:6px;overflow:auto;">'+tabsHtml+'</div>'+body+'</div>';
}