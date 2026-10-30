'use strict';
document.addEventListener('DOMContentLoaded', () => {
  const app = window.medicare, $ = (s) => document.querySelector(s);
  const basket = new Map();
  let kind = '', next = null, previous = null, busy = false, loadVersion = 0;
  const pendingKey = 'medicare.pending-checkout.' + $('#checkout-form').dataset.userId;
  const showError = message => { $('#checkout-error').textContent = message; $('#checkout-error').classList.remove('hidden'); };
  function totals() {
    const rows = [...basket.values()].sort((a,b) => a.product.id-b.product.id);
    const subtotal = rows.reduce((s, r) => s + Math.round(Number(r.product.price)*100)*r.quantity, 0);
    const discount = Math.max(0, Math.min(subtotal, Math.round(Number($('#discount')?.value || 0)*100)));
    let remaining = discount, remainingSubtotal = subtotal, tax = 0;
    rows.forEach((r, index) => {
      const gross = Math.round(Number(r.product.price)*100)*r.quantity;
      const share = index === rows.length-1 ? remaining : Math.min(remaining, Math.round(remaining*gross/remainingSubtotal));
      remaining -= share; remainingSubtotal -= gross;
      tax += Math.round((gross-share)*Number(r.product.tax_rate)/100);
    });
    return {subtotal: subtotal/100, tax: tax/100, total: (subtotal-discount+tax)/100};
  }
  function renderBasket() {
    const container = $('#basket-items'); container.replaceChildren();
    if (!basket.size) container.append(app.node('div', 'Your basket is empty. Select a product to get started.', 'empty'));
    let count = 0;
    basket.forEach((row, id) => {
      count += row.quantity;
      const el = app.node('div', undefined, 'basket-row');
      const info = app.node('div', undefined, 'grow'); info.append(app.node('strong', row.product.name), app.node('small', `${row.product.sku} · ${app.money(row.product.price)}`));
      const input = app.node('input'); input.type='number'; input.min='1'; input.max=String(row.product.stock); input.value=String(row.quantity); input.setAttribute('aria-label', `Quantity for ${row.product.name}`);
      input.addEventListener('change', () => { row.quantity=Math.max(1, Math.min(row.product.stock, Number.parseInt(input.value,10)||1)); renderBasket(); });
      const remove = app.node('button', '×', 'icon-button'); remove.type='button'; remove.setAttribute('aria-label', `Remove ${row.product.name}`); remove.onclick=()=>{basket.delete(id);renderBasket();};
      el.append(info,input,remove); container.append(el);
    });
    const t = totals(); $('#basket-count').textContent=count; $('#subtotal').textContent=app.money(t.subtotal); $('#tax').textContent=app.money(t.tax); $('#grand-total').textContent=app.money(t.total);
    $('#checkout-button').disabled=!basket.size || busy;
    if ($('#payment-method').value !== 'CASH') $('#amount-paid').value=t.total.toFixed(2);
  }
  async function load(url) {
    const version = ++loadVersion;
    $('#catalog-status').textContent='Loading catalog…';
    try {
      const data = await app.request(url || `/api/v1/products/?q=${encodeURIComponent($('#pos-search').value)}&kind=${kind}`);
      if (version !== loadVersion) return;
      next=data.next;previous=data.previous;
      $('#catalog-next').disabled=!next;$('#catalog-prev').disabled=!previous;
      const container=$('#pos-products');container.replaceChildren();
      data.results.filter(p=>p.active).forEach(p=>{
        const card=app.node('button',undefined,'product-card');card.type='button';card.disabled=p.stock<1;
        card.append(app.node('span',p.kind==='MEDICINE'?'℞':'▦','product-icon'),app.node('strong',p.name),app.node('small',`${p.sku}${p.prescription_required?' · Prescription':''}`),app.node('b',app.money(p.price)),app.node('span',`${p.stock} ${p.unit} available`,'stock-note'));
        card.onclick=()=>{const row=basket.get(p.id);if(row){row.quantity=Math.min(row.quantity+1,p.stock);}else basket.set(p.id,{product:p,quantity:1});renderBasket();};
        container.append(card);
      });
      $('#catalog-status').textContent=container.children.length ? `${data.count} matching products · select to add` : 'No active products match your search.';
    } catch(error) { if(version===loadVersion) $('#catalog-status').textContent=error.message; }
  }
  $('#pos-search-form').onsubmit=event=>{event.preventDefault();load();};
  document.querySelectorAll('[data-kind]').forEach(b=>b.onclick=()=>{kind=b.dataset.kind;document.querySelectorAll('[data-kind]').forEach(n=>n.classList.toggle('selected',n===b));load();});
  $('#catalog-next').onclick=()=>next&&load(next);$('#catalog-prev').onclick=()=>previous&&load(previous);
  $('#discount')?.addEventListener('input',renderBasket);$('#payment-method').onchange=renderBasket;
  $('#prescription')?.addEventListener('change',()=>{const selected=$('#prescription').selectedOptions[0];if(selected.dataset.patient)$('#patient').value=selected.dataset.patient;});
  $('#clear-basket').onclick=()=>{if(!busy && window.confirm('Clear this basket?')){basket.clear();renderBasket();}};
  async function submit(payload) {
    busy=true;$('#checkout-button').disabled=true;$('#checkout-button').textContent='Completing sale…';$('#checkout-error').classList.add('hidden');
    // Keep the exact pending payload across connection failures and browser reloads.
    sessionStorage.setItem(pendingKey,JSON.stringify(payload));
    try {
      const sale=await app.request('/api/v1/sales/',{method:'POST',body:JSON.stringify(payload)});
      sessionStorage.removeItem(pendingKey);window.location.assign(`/sales/${sale.id}/`);
    } catch(error) {
      if(error.status && error.status<500 && error.status!==409) sessionStorage.removeItem(pendingKey);
      showError(error.message + (sessionStorage.getItem(pendingKey)?' A pending request is saved. Retry it before starting another sale; check Sales & receipts if the outcome is uncertain.':''));
      if(sessionStorage.getItem(pendingKey)) addRetry();
      busy=false;$('#checkout-button').textContent='Complete sale →';renderBasket();
    }
  }
  function addRetry(){
    if($('#retry-pending'))return;
    const button=app.node('button','Retry saved checkout','button');button.id='retry-pending';button.type='button';
    button.onclick=()=>{if(!busy)submit(JSON.parse(sessionStorage.getItem(pendingKey)));};
    const discard=app.node('button','Discard saved request','text-button');discard.type='button';discard.onclick=()=>{if(window.confirm('Check Sales & receipts first. Discard this pending request? A completed sale will not be reversed.')){sessionStorage.removeItem(pendingKey);button.remove();discard.remove();}};
    $('#checkout-error').after(button,discard);
  }
  $('#checkout-form').onsubmit=event=>{
    event.preventDefault();if(busy || !basket.size)return;
    if(sessionStorage.getItem(pendingKey)){showError('Resolve the saved checkout before starting another sale.');addRetry();return;}
    const payload={idempotency_key:crypto.randomUUID(),items:[...basket.values()].map(r=>({product_id:r.product.id,quantity:r.quantity})),discount:$('#discount')?.value||'0',amount_paid:$('#amount-paid').value,payment_method:$('#payment-method').value};
    if($('#patient')?.value)payload.patient_id=Number($('#patient').value);
    if($('#prescription')?.value)payload.prescription_id=Number($('#prescription').value);
    submit(payload);
  };
  if(sessionStorage.getItem(pendingKey)){showError('There is a checkout with an unresolved outcome. Retry the saved request or review Sales & receipts.');addRetry();}
  load();
});
