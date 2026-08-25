function renderHome(t, ar) {
  var tickerHtml = TICKER.map(function(tk){
    var color = tk.up===true?'#064E3B':tk.up===false?'#DC5B4B':'#9CA3AF';
    return '<div style="flex-shrink:0;display:flex;align-items:center;gap:6px;background:#fff;border-radius:9999px;padding:7px 12px;box-shadow:0 2px 6px rgba(15,23,42,.05);"><span style="font-size:12px;font-weight:700;color:#0F172A;">'+tk[state.lang]+'</span><span style="font-size:11px;font-weight:700;color:'+color+';">'+tk.delta+'</span></div>';
  }).join('');

  var scHtml = SHORTCUTS.map(function(s, idx){
    return '<div data-act="goTo" data-arg="'+s.go+'" class="press" style="display:flex;flex-direction:column;align-items:center;gap:6px;cursor:pointer;transition:transform .15s cubic-bezier(.34,1.56,.64,1);">'
      + '<div style="width:46px;height:46px;border-radius:15px;background:'+s.bg+';display:flex;align-items:center;justify-content:center;box-shadow:0 1px 2px rgba(15,23,42,.04),0 6px 14px rgba(15,23,42,.06);animation:zadFadeUp .45s ease '+(idx*0.06)+'s both;">'+icon(s.icon,20,'stroke:'+s.fg)+'</div>'
      + '<span style="font-size:10px;font-weight:600;color:#6B7280;text-align:center;">'+s[state.lang]+'</span></div>';
  }).join('');

  var kindDot = {info:'#064E3B',warn:'#B45309',danger:'#DC5B4B'};
  var kindTagBg = {info:'rgba(6,78,59,.1)',warn:'rgba(180,83,9,.1)',danger:'rgba(220,91,75,.12)'};
  var kindTag = {info:ar?'معلومة':'Info',warn:ar?'مطلوب':'Action',danger:ar?'تحذير':'Alert'};
  var insightsHtml = INSIGHTS.map(function(i, idx){
    return '<div class="press" style="background:rgba(255,255,255,.85);backdrop-filter:blur(14px);border:1px solid rgba(0,0,0,.05);border-radius:16px;padding:14px 16px;display:flex;gap:12px;align-items:flex-start;box-shadow:0 2px 4px rgba(15,23,42,.03),0 12px 28px rgba(15,23,42,.06);animation:zadFadeUp .45s ease '+(idx*0.09)+'s both;">'
      + '<div style="width:10px;height:10px;border-radius:50%;background:'+kindDot[i.kind]+';margin-top:5px;flex-shrink:0;"></div>'
      + '<div style="flex:1;display:flex;flex-direction:column;gap:4px;"><span style="font-size:14.5px;font-weight:600;color:#0F172A;">'+i[state.lang].title+'</span><span style="font-size:13px;color:#6B7280;line-height:1.4;">'+i[state.lang].subtitle+'</span></div>'
      + '<span style="font-size:11px;font-weight:700;border-radius:9999px;padding:4px 9px;background:'+kindTagBg[i.kind]+';color:'+kindDot[i.kind]+';flex-shrink:0;white-space:nowrap;">'+kindTag[i.kind]+'</span></div>';
  }).join('');

  var statsHtml = HOME_STATS.map(function(s, idx){
    return card('<span style="font-size:11px;font-weight:600;color:#9CA3AF;">'+s[state.lang]+'</span><br><span style="font-size:18px;font-weight:700;color:#0F172A;">'+(ar?s.valAr:s.valEn)+'</span>', 'animation:zadFadeUp .4s ease '+(idx*0.07)+'s both;display:flex;flex-direction:column;gap:4px;');
  }).join('');

  var aiChips = (ar?['أوقف اشتراك؟','زود الميزانية','شوف التفاصيل']:['Cancel a sub?','Increase budget','See details']).map(function(c){
    return '<span style="background:rgba(255,255,255,.1);color:#fff;font-size:11.5px;font-weight:600;border-radius:9999px;padding:6px 12px;">'+c+'</span>';
  }).join('');

  var gardenPct = Math.round((state.tasbihaCount/100)*100);
  var gardenEmoji = state.tasbihaCount>=100?'🌳':state.tasbihaCount>=75?'🌿🌿':state.tasbihaCount>=50?'🦋':state.tasbihaCount>=25?'🌿':'🌱';
  var confettiHtml = state.showConfetti ? '<div style="position:absolute;inset:0;pointer-events:none;overflow:visible;">'+['🍃','🌸','🍃','🌸','🍃','🌸','🍃','🌸','🍃','🌸'].map(function(e,i){
    return '<span style="position:absolute;left:'+(10+i*8)+'%;top:50%;font-size:16px;animation:zadRise '+(1.2+(i%3)*0.3)+'s ease-out '+(i*0.05)+'s forwards;">'+e+'</span>';
  }).join('')+'</div>' : '';

  var amazonHtml = AMAZON_DEALS.map(function(d){
    return '<div style="flex-shrink:0;width:140px;background:#fff;border-radius:16px;padding:10px;display:flex;flex-direction:column;gap:8px;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);">'
      + '<div style="width:100%;height:80px;border-radius:10px;background:#F1F4F3;display:flex;align-items:center;justify-content:center;color:#9CA3AF;font-size:11px;text-align:center;padding:6px;">'+d[state.lang]+'</div>'
      + '<span style="font-size:12px;font-weight:600;color:#0F172A;line-height:1.3;">'+d[state.lang]+'</span>'
      + '<div style="display:flex;justify-content:space-between;align-items:center;"><span style="font-size:13px;font-weight:800;color:#B45309;">'+(ar?d.price:d.priceEn)+'</span><span style="font-size:9.5px;font-weight:700;color:#9CA3AF;">'+t.amazonBadge+'</span></div></div>';
  }).join('');

  var txHtml = HOME_TX.map(function(x, idx){
    var color = x.neg?'#DC5B4B':'#064E3B';
    return '<div style="background:#fff;border-radius:14px;padding:12px 14px;display:flex;justify-content:space-between;align-items:center;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);animation:zadFadeUp .4s ease '+(idx*0.07)+'s both;">'
      + '<div style="display:flex;flex-direction:column;gap:2px;"><span style="font-size:13.5px;font-weight:600;color:#0F172A;">'+x[state.lang]+'</span><span style="font-size:11.5px;color:#9CA3AF;">'+(ar?x.dateAr:x.dateEn)+'</span></div>'
      + '<span style="font-size:14px;font-weight:700;color:'+color+';">'+(ar?x.amountAr:x.amountEn)+'</span></div>';
  }).join('');

  return '<div style="padding:18px 20px 130px;display:flex;flex-direction:column;gap:18px;animation:zadFadeUp .4s ease both;">'
    + '<div style="display:flex;gap:8px;overflow:auto;">'+tickerHtml+'</div>'
    + '<div data-act="openSheet" class="press" style="background:linear-gradient(120deg,#0B6B4E,#0F9B76,#064E3B,#0B6B4E);background-size:300% 300%;animation:zadMeshShift 9s ease infinite;border:1px solid rgba(255,255,255,.16);border-radius:28px;padding:26px 24px 22px;color:#fff;cursor:pointer;display:flex;flex-direction:column;gap:16px;box-shadow:inset 0 1px 0 rgba(255,255,255,.2),0 20px 44px rgba(6,78,59,.35);">'
    + '<span style="font-size:11.5px;font-weight:700;letter-spacing:.4px;color:rgba(255,255,255,.72);">'+t.available+'</span>'
    + '<span style="font-size:44px;font-weight:800;letter-spacing:-1.2px;background:linear-gradient(180deg,#fff,#D9F2E6);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;">'+(ar?'≈ 3,240 ر.س':'≈ SAR 3,240')+'</span>'
    + '<div style="display:flex;gap:10px;flex-wrap:wrap;">'
    + '<div style="display:flex;align-items:center;gap:7px;background:rgba(255,255,255,.14);backdrop-filter:blur(10px);border:1px solid rgba(255,255,255,.14);border-radius:9999px;padding:7px 13px;font-size:12.5px;font-weight:600;"><div style="width:7px;height:7px;border-radius:50%;background:#F4A93B;box-shadow:0 0 8px 2px rgba(244,169,59,.7);"></div>'+t.spent+': '+(ar?'1,120 ر.س':'SAR 1,120')+'</div>'
    + '<div style="display:flex;align-items:center;gap:7px;background:rgba(255,255,255,.14);backdrop-filter:blur(10px);border:1px solid rgba(255,255,255,.14);border-radius:9999px;padding:7px 13px;font-size:12.5px;font-weight:600;"><div style="width:7px;height:7px;border-radius:50%;background:#FF8066;box-shadow:0 0 8px 2px rgba(255,128,102,.7);"></div>'+t.committed+': '+(ar?'640 ر.س':'SAR 640')+'</div>'
    + '</div></div>'
    + '<div style="display:flex;gap:12px;">'
    + card('<span style="font-size:11.5px;font-weight:600;color:#6B7280;">'+t.daysLeft+'</span><br><span style="font-size:24px;font-weight:700;color:#0F172A;">'+t.daysLeftValue+'</span>','flex:1;display:flex;flex-direction:column;gap:4px;')
    + card('<span style="font-size:11.5px;font-weight:600;color:#6B7280;">'+t.safeSpend+'</span><br><span style="font-size:24px;font-weight:700;color:#0F172A;">'+t.safeSpendValue+'</span>','flex:1;display:flex;flex-direction:column;gap:4px;')
    + '</div>'
    + '<div style="display:grid;grid-template-columns:repeat(6,1fr);gap:8px;">'+scHtml+'</div>'
    + '<div style="display:flex;flex-direction:column;gap:10px;"><span style="font-size:15px;font-weight:700;color:#0F172A;">'+t.insightsTitle+'</span>'+insightsHtml+'</div>'
    + '<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;">'+statsHtml+'</div>'
    + '<div style="background:#052E16;border-radius:20px;padding:18px;display:flex;flex-direction:column;gap:10px;"><span style="font-size:12.5px;font-weight:700;color:#6EE7B7;">'+t.aiSummaryTitle+'</span><span style="font-size:14px;color:#fff;line-height:1.55;">'+(ar?'صرفك هذا الأسبوع أعلى من المعتاد بـ 12%، لكن مخزونك ودورة راتبك تحت السيطرة.':'Spend this week is 12% above usual, but your stock and salary cycle are under control.')+'</span><div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:4px;">'+aiChips+'</div></div>'
    + '<div style="background:rgba(255,255,255,.7);backdrop-filter:blur(14px);border:1px solid rgba(0,0,0,.05);border-radius:18px;padding:16px 18px;display:flex;flex-direction:column;gap:10px;box-shadow:0 2px 4px rgba(15,23,42,.03),0 12px 28px rgba(15,23,42,.06);">'
    + '<div style="display:flex;justify-content:space-between;align-items:center;"><span style="font-size:13px;font-weight:700;color:#374151;">'+t.tasbihaTitle+'</span><span style="font-size:11.5px;font-weight:700;color:#0F9B76;">'+gardenPct+'%</span></div>'
    + '<div style="display:flex;align-items:center;justify-content:center;padding:10px 0;position:relative;"><span class="press" style="font-size:52px;line-height:1;display:inline-block;">'+gardenEmoji+'</span>'+confettiHtml+'</div>'
    + '<div style="height:8px;border-radius:99px;background:rgba(15,23,42,.08);overflow:hidden;"><div style="width:'+gardenPct+'%;height:100%;border-radius:99px;background:linear-gradient(90deg,#7C3AED,#0F9B76);transition:width .4s ease;"></div></div>'
    + '<div style="display:flex;justify-content:space-between;align-items:center;"><span style="font-size:12px;color:#6B7280;">'+state.tasbihaCount+' / 100</span><button data-act="tapTasbiha" class="press" style="border:none;background:#7C3AED;color:#fff;font-size:13px;font-weight:700;border-radius:9999px;padding:9px 20px;cursor:pointer;box-shadow:0 8px 18px rgba(124,58,237,.3);">'+t.tasbihaTap+'</button></div></div>'
    + '<div style="background:#fff;border-radius:18px;padding:16px;display:flex;gap:14px;align-items:center;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);">'
    + '<div style="width:56px;height:56px;border-radius:16px;background:#FDF3E1;display:flex;align-items:center;justify-content:center;flex-shrink:0;"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#B45309" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round"><path d="M6 13a6 6 0 1112 0v1H6v-1z"/><path d="M5 14h14v2a1 1 0 01-1 1H6a1 1 0 01-1-1v-2z" fill="#B45309" stroke="none"/><path d="M9 21h6"/></svg></div>'
    + '<div style="display:flex;flex-direction:column;gap:4px;"><span style="font-size:14px;font-weight:700;color:#0F172A;">'+t.chefTitle+'</span><span style="font-size:12.5px;color:#6B7280;line-height:1.4;">'+t.chefSub+'</span></div></div>'
    + '<div style="display:flex;flex-direction:column;gap:10px;"><span style="font-size:15px;font-weight:700;color:#0F172A;">'+t.amazonTitle+'</span><div style="display:flex;gap:12px;overflow:auto;padding-bottom:2px;">'+amazonHtml+'</div></div>'
    + '<div style="display:flex;flex-direction:column;gap:10px;"><div style="display:flex;justify-content:space-between;align-items:center;"><span style="font-size:15px;font-weight:700;color:#0F172A;">'+t.recentTx+'</span><span data-act="goTo" data-arg="budget" style="font-size:12.5px;font-weight:600;color:#064E3B;cursor:pointer;">'+t.seeAll+'</span></div>'+txHtml+'</div>'
    + '</div>';
}