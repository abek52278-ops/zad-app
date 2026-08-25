function renderInventory(t, ar) {
  var invCatMap = {}; INV_CATEGORIES.forEach(function(c){ invCatMap[c.id] = c; });
  var allCats = [{id:'all',icon:'M4 6h16M4 12h16M4 18h16',bg:'#EDEEF0',fg:'#374151',ar:'الكل',en:'All'}].concat(INV_CATEGORIES);
  var chipsHtml = allCats.map(function(c, idx){
    var on = state.invCat === c.id;
    return '<button data-act="setInvCat" data-arg="'+c.id+'" style="flex-shrink:0;display:flex;align-items:center;gap:6px;border:none;border-radius:9999px;padding:6px 12px 6px 6px;font-size:12.5px;font-weight:700;cursor:pointer;background:'+(on?'#064E3B':'#fff')+';color:'+(on?'#fff':'#374151')+';box-shadow:'+(on?'none':'0 1px 2px rgba(15,23,42,.04),0 6px 14px rgba(15,23,42,.06)')+';animation:zadFadeUp .35s ease '+(idx*0.04)+'s both;">'
      + '<div style="width:20px;height:20px;border-radius:50%;background:'+(on?'rgba(255,255,255,.2)':c.bg)+';display:flex;align-items:center;justify-content:center;flex-shrink:0;'+(on?'animation:zadCarrotFloat 1.6s ease-in-out infinite;':'')+'">'+icon(c.icon,13,'stroke:'+(on?'#fff':c.fg))+'</div>'+c[state.lang]+'</button>';
  }).join('');

  var filtered = INVENTORY.filter(function(it){ return state.invCat==='all' || it.cat===state.invCat; });
  var itemsHtml = filtered.map(function(it, idx){
    var origIdx = INVENTORY.indexOf(it);
    var confirmed = !!state.confirmed[origIdx];
    var showConfirm = it.low && !confirmed;
    var pct = Math.min(100, (it.days/20)*100);
    var color = it.days<=2?'#DC5B4B':it.days<=6?'#B45309':'#064E3B';
    var cat = invCatMap[it.cat];
    var confirmBtn = showConfirm ? '<button data-act="confirmItem" data-arg="'+origIdx+'" style="border:none;background:rgba(228,87,46,.12);color:#DC5B4B;font-size:10.5px;font-weight:700;border-radius:9999px;padding:3px 8px;cursor:pointer;">'+(ar?'تأكيد':'Confirm')+'</button>' : '';
    return '<div style="background:#fff;border-radius:16px;padding:14px;display:flex;flex-direction:column;gap:8px;box-shadow:0 1px 2px rgba(15,23,42,.04),0 8px 20px rgba(15,23,42,.07);animation:zadFadeUp .4s ease '+(idx*0.05)+'s both;">'
      + '<div style="display:flex;justify-content:space-between;align-items:flex-start;"><div style="display:flex;align-items:center;gap:8px;"><div style="width:30px;height:30px;border-radius:9px;background:'+cat.bg+';display:flex;align-items:center;justify-content:center;flex-shrink:0;">'+icon(cat.icon,15,'stroke:'+cat.fg)+'</div><span style="font-size:14px;font-weight:600;color:#0F172A;">'+it[state.lang]+'</span></div>'+confirmBtn+'</div>'
      + '<span style="font-size:12px;color:#9CA3AF;">'+cat[state.lang]+'</span>'
      + '<div style="height:5px;border-radius:99px;background:#F1F4F3;overflow:hidden;"><div style="width:'+pct+'%;height:100%;border-radius:99px;background:'+color+';transition:width .7s ease '+(idx*0.05)+'s;"></div></div>'
      + '<span style="font-size:11.5px;font-weight:600;color:#6B7280;">'+(ar?('متبقي '+it.days+' يوم'):(it.days+'d left'))+'</span></div>';
  }).join('');

  return '<div style="padding:18px 20px 130px;display:flex;flex-direction:column;gap:14px;animation:zadFadeUp .4s ease both;">'
    + '<div style="background:rgba(220,91,75,.08);border-radius:14px;padding:12px 14px;font-size:12.5px;font-weight:600;color:#DC5B4B;">'+(ar?'ثلاثة عناصر منخفضة: موز، حليب، خبز':'3 items low: Bananas, Milk, Bread')+'</div>'
    + '<div style="display:flex;gap:8px;overflow:auto;padding-bottom:2px;">'+chipsHtml+'</div>'
    + '<div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;">'+itemsHtml+'</div></div>';
}