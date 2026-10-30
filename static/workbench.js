'use strict';
document.addEventListener('DOMContentLoaded',()=>{
  const app=window.medicare, form=document.querySelector('#workbench-form');
  if(!form)return;
  const mode=form.dataset.mode, container=document.querySelector('#workbench-lines');
  function add(){if(container.children.length>=100)return;const row=document.querySelector('#line-template').content.cloneNode(true);row.querySelector('.remove-line').onclick=event=>event.target.closest('.workbench-line').remove();container.append(row);}
  document.querySelector('#add-line').onclick=add;add();
  form.onsubmit=async event=>{
    event.preventDefault();const button=document.querySelector('#save-document'), errorBox=document.querySelector('#workbench-error');
    const rows=[...container.querySelectorAll('.workbench-line')];
    if(!rows.length){errorBox.textContent='Add at least one line item.';errorBox.classList.remove('hidden');return;}
    const items=rows.map(row=>({product_id:Number(row.querySelector('.line-product').value),quantity:Number(row.querySelector('.line-quantity').value),...(mode==='purchase'?{unit_cost:row.querySelector('.line-cost').value}:{directions:row.querySelector('.line-directions').value})}));
    let payload={items};
    if(mode==='purchase')payload.supplier_id=Number(document.querySelector('#supplier').value);
    else payload={...payload,patient_id:Number(document.querySelector('#patient').value),reference:document.querySelector('#reference').value,prescriber:document.querySelector('#prescriber').value,prescriber_registration:document.querySelector('#registration').value,issued_on:document.querySelector('#issued').value,valid_until:document.querySelector('#valid-until').value};
    button.disabled=true;errorBox.classList.add('hidden');
    try{const result=await app.request(`/api/v1/${mode==='purchase'?'purchases':'prescriptions'}/`,{method:'POST',body:JSON.stringify(payload)});window.location.assign(mode==='purchase'?`/purchases/${result.id}/`:'/prescriptions/');}
    catch(error){errorBox.textContent=error.message;errorBox.classList.remove('hidden');button.disabled=false;}
  };
});
