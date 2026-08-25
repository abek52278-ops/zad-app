function renderAssistant(t, ar) {
  var tabs = [['overview',ar?'نظرة عامة':'Overview'],['behavior',ar?'السلوك':'Behavior'],['chat',ar?'المحادثة':'Chat']];
  var tabsHtml = tabs.map(function(tb){ return segBtn(tb[1], state.assistantTab===tb[0], 'setAssistantTab', tb[0]); }).join('');

  var body = '';
  if (state.assistantTab === 'overview') {
    var max = Math.max.apply(null, SPEND_TREND), min = Math.min.apply(null, SPEND_TREND);
    var w=280, h=64;
    var pts = SPEND_TREND.map(function(v,i){ return (i/(SPEND_TREND.length-1)*w)+','+(h-((v-min)/((max-min)||1))*(h-8)-4); });
    var sparkPoints = pts.join(' ');
    var sparkFillPoints = '0,'+h+' '+pts.join(' ')+' '+w+','+h;
    var catBarsHtml = CATEGORY_SPEND.map(function(c){
      return '<div style="display:flex;flex-direction:column;gap:5px;"><div style="display:flex;justify-content:space-between;"><span style="font-size:12.5px;font-weight:600;color:#374151;">'+c[state.lang]+'</span><span style="font-size:12px;font-weight:700;color:#0F172A;">'+(ar?(c.amt+' ر.س'):('SAR '+c.amt))+'</span></div><div style="height:7px;border-radius:99px;background:#F1F4F3;overflow:hidden;"><div style="width:'+Math.round((c.amt/c.max)*100)+'%;height:100%;border-radius:99px;background:'+c.color+';transition:width .6s ease;"></div></div></div>';
    }).join('');
    var ovHtml = ASSIST_OVERVIEW.map(function(o){
      return card('<span style="font-size:11px;font-weight:600;color:#9CA3AF;">'+o[state.lang]+'</span><br><span style="font-size:18px;font-weight:700;color:#0F172A;">'+(ar?o.valAr:o.valEn)+'</span>','display:flex;flex-direction:column;gap:4px;');
    }).join('');
    body = '<div style="display:flex;flex-direction:column;gap:12px;">'
      + '<div style="background:#052E16;border-radius:20px;padding:18px;display:flex;flex-direction:column;gap:10px;color:#fff;"><span style="font-size:12.5px;font-weight:700;color:#6EE7B7;">'+t.spendingPower+'</span><span style="font-size:30px;font-weight:800;">82%</span><div style="height:8px;border-radius:99px;background:rgba(255,255,255,.15);overflow:hidden;"><div style="width:82%;height:100%;border-radius:99px;background:#6EE7B7;"></div></div></div>'
      + '<div style="background:#fff;border-radius:18px;padding:16px;display:flex;flex-direction:column;gap:10px;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);"><div style="display:flex;justify-content:space-between;align-items:baseline;"><span style="font-size:13px;font-weight:700;color:#0F172A;">'+t.liveSpendTitle+'</span><span style="font-size:11px;font-weight:600;color:#9CA3AF;">'+t.liveSpendSub+'</span></div><svg width="100%" height="64" viewBox="0 0 280 64" preserveAspectRatio="none"><polyline points="'+sparkPoints+'" fill="none" stroke="#0F9B76" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/><polygon points="'+sparkFillPoints+'" fill="url(#sparkGrad)" opacity=".5"/><defs><linearGradient id="sparkGrad" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0F9B76" stop-opacity=".4"/><stop offset="1" stop-color="#0F9B76" stop-opacity="0"/></linearGradient></defs></svg></div>'
      + '<div style="background:#fff;border-radius:18px;padding:16px;display:flex;flex-direction:column;gap:12px;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);"><span style="font-size:13px;font-weight:700;color:#0F172A;">'+t.categoryBreakdown+'</span><div style="display:flex;flex-direction:column;gap:10px;">'+catBarsHtml+'</div></div>'
      + '<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;">'+ovHtml+'</div></div>';
  } else if (state.assistantTab === 'behavior') {
    body = '<div style="display:flex;flex-direction:column;gap:10px;">'+ASSIST_BEHAVIOR.map(function(b){
      return '<div style="background:#fff;border-radius:16px;padding:14px 16px;display:flex;flex-direction:column;gap:4px;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);"><span style="font-size:14px;font-weight:600;color:#0F172A;">'+b[state.lang].title+'</span><span style="font-size:12.5px;color:#6B7280;line-height:1.4;">'+b[state.lang].body+'</span></div>';
    }).join('')+'</div>';
  } else {
    body = '<div style="display:flex;flex-direction:column;gap:12px;">'+CHATS.map(function(m){
      var right = m.user;
      return '<div style="display:flex;justify-content:'+(right?'flex-end':'flex-start')+';"><div style="max-width:78%;padding:11px 15px;border-radius:16px;font-size:14px;line-height:1.45;background:'+(right?'#064E3B':'#fff')+';color:'+(right?'#fff':'#0F172A')+';box-shadow:'+(right?'none':'0 2px 8px rgba(15,23,42,.05)')+';">'+m[state.lang]+'</div></div>';
    }).join('')+'<button data-act="openSheet" style="align-self:flex-start;border:1px solid rgba(6,78,59,.2);background:#fff;color:#064E3B;font-size:12.5px;font-weight:600;border-radius:9999px;padding:8px 14px;cursor:pointer;">'+t.quickChip+'</button></div>';
  }

  return '<div style="padding:14px 20px 130px;display:flex;flex-direction:column;gap:14px;animation:zadFadeUp .4s ease both;">'
    + '<div style="display:flex;gap:6px;background:#fff;border-radius:9999px;padding:4px;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);">'+tabsHtml+'</div>'+body+'</div>';
}