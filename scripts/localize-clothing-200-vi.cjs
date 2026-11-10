// Local editorial rewrite from source facts. No external translation requests.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const dir=path.resolve(__dirname,'../artifacts/catalog-review/livostyle');
const read=n=>JSON.parse(fs.readFileSync(path.join(dir,n),'utf8'));
const canon=s=>s.trim().replace(/\s+/g,' ').toUpperCase();
const materialMap={viscose:'sợi viscose',polyester:'sợi polyester',rayon:'sợi rayon',spandex:'sợi co giãn spandex',elastane:'sợi co giãn elastane',nylon:'sợi nylon',polyamide:'sợi polyamide',cotton:'bông',acrylic:'sợi acrylic',wool:'len',ribbed:'vải dệt gân',knit:'vải dệt kim',denim:'vải bò',mesh:'vải lưới',lace:'ren',terry:'vải da cá',fleece:'vải lông cào',satin:'sa tanh',velvet:'nhung',suede:'da lộn','cotton blend':'bông pha','see product details':'Xem mô tả sản phẩm'};
function material(s){if(s==null)return null;return s.split(',').map(t=>{const v=materialMap[t.trim().toLowerCase()];if(!v)throw Error('UNMAPPED_MATERIAL: '+t);return v;}).join(', ');}
const fits={Regular:'tiêu chuẩn',Relaxed:'rộng thoải mái',Slim:'ôm gọn',Fitted:'ôm người',Cropped:'dáng ngắn',Oversized:'rộng',Wide:'rộng',Tailored:'đứng dáng','High-waisted':'cạp cao','High waist':'cạp cao','High waisted':'cạp cao','High rise':'cạp cao',Tiered:'xếp tầng','A-Line':'chữ A',Wrap:'đắp chéo'};
const headers={'Size':'Cỡ','US':'Cỡ Hoa Kỳ','US Size':'Cỡ Hoa Kỳ','Letter Size':'Cỡ chữ','Front Length':'Dài thân trước','Sleeve Length':'Dài tay','Bust':'Vòng ngực','Length':'Chiều dài','Under Bust':'Vòng chân ngực','Waist':'Vòng eo','Hip':'Vòng mông','Shoulder':'Ngang vai','Top Length':'Dài áo','Shoulder (with sleeve)':'Ngang vai (gồm tay)','Bottom Length':'Dài phần dưới','Pants Length':'Dài quần','Inseam':'Dài ống trong','Hem':'Rộng gấu','Rise':'Dài đáy','Low Hip':'Vòng mông dưới','Waist Stretch Amount':'Độ giãn vòng eo','Bust Stretch Amount':'Độ giãn vòng ngực','Waist (Relaxed)':'Eo khi thả lỏng','Waist (Max Stretch)':'Eo khi giãn tối đa'};
function plain(s){return s.replace(/<[^>]*>/g,' ').replace(/&nbsp;|&#160;/g,' ').replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&#39;|&apos;/g,"'").replace(/&#(\d+);/g,(_,n)=>String.fromCodePoint(+n)).replace(/\s+/g,' ').trim();}
function table(p){
 const html=[p.metafields?.supplier_description,p.description_html].filter(Boolean).find(h=>/<table\b/i.test(h));
 if(!html)return {text:'',cells:[]};
 const result=[],original=[];
 for(const match of html.matchAll(/<tr\b[^>]*>([\s\S]*?)<\/tr>/gi)){
  const cells=[...match[1].matchAll(/<(th|td)\b[^>]*>([\s\S]*?)<\/\1>/gi)];
  if(!cells.length)continue;
  result.push(cells.map(m=>{const t=plain(m[2]);if(m[1].toLowerCase()==='th'){
   const inch=/\(in\)$/.test(t);const label=headers[t.replace(/\s*\(in\)$/,'')];if(!label)throw Error('UNMAPPED_HEADER: '+t);return label+(inch?' (inch)':'');
  }original.push(t);return t.replace(/\bone size\b/gi,'Một cỡ').replace(/\bplus size\b/gi,'cỡ lớn').replace(/\bin\b/g,'inch');}).join(' | '));
 }
 const inch=/measurements?[^<]{0,70}inch|measurements? by inches|\(in\)/i.test(html);
 return {text:'Thông số kích thước'+(inch?' (đơn vị inch)':' theo nguồn')+':\n'+result.join('\n'),cells:original};
}
function care(p){
 const html=p.metafields?.supplier_description||'';
 const explicit=[...html.matchAll(/<li[^>]*>([\s\S]*?)<\/li>/gi)].map(m=>plain(m[1])).filter(s=>/^care(?: instructions)?:/i.test(s)).join(' ');
 const segment=p.description.match(/(?:Fabric|Material)\s*&\s*care([\s\S]*?)Fit\s*&\s*sizing/i)?.[1]||'';
 const source=explicit||(/wash|dry clean/i.test(segment)?segment:p.metafields?.care)||'';
 const out=[];
 if(/dry clean only/i.test(source))out.push('Chỉ giặt khô.');
 else {if(/machine wash(?:ed)? cold/i.test(source))out.push('Giặt máy bằng nước lạnh.');if(/hand wash cold/i.test(source))out.push('Giặt tay bằng nước lạnh.');if(/dry clean/i.test(source))out.push('Có thể giặt khô.');}
 if(/similar colors|like colors/i.test(source))out.push('Giặt cùng các màu tương tự.');
 if(/do not tumble dry/i.test(source))out.push('Không sấy bằng máy.');
 else if(/tumble dr(?:y|ied) low/i.test(source))out.push('Sấy ở nhiệt độ thấp.');
 if(/lay flat/i.test(source))out.push('Trải phẳng khi phơi.');if(/hang dry/i.test(source))out.push('Có thể phơi treo.');
 if(/do not bleach/i.test(source))out.push('Không dùng chất tẩy.');if(/iron on low/i.test(source))out.push('Ủi ở nhiệt độ thấp khi cần.');if(/do not wring/i.test(source))out.push('Không vắt xoắn.');
 return out.length?out.join(' '):'Tham khảo hướng dẫn trên nhãn sản phẩm.';
}
function composition(p){
 const html=p.metafields?.supplier_description||p.description_html||'';
 const source=plain(html)+' '+p.description;
 const seen=new Set(),parts=[];
 for(const m of source.matchAll(/(\d+(?:\.\d+)?)%\s*(recycled\s+)?(polyester|viscose|rayon|spandex|elastane|nylon|polyamide|cotton|acrylic|wool)\b/gi)){
  const key=m[1]+'% '+(m[2]||'').toLowerCase()+m[3].toLowerCase();if(seen.has(key))continue;seen.add(key);
  parts.push({percent:+m[1],text:m[1]+'% '+materialMap[m[3].toLowerCase()]+(m[2]?' tái chế':'')});
 }
 if(!parts.length)return {text:material(p.metafields?.material||p.metafields?.fabric)||'Nguồn chưa cung cấp thành phần chất liệu.',ambiguous:false};
 const sum=parts.reduce((n,x)=>n+x.percent,0);
 if(Math.abs(sum-100)>0.01)return {text:material(p.metafields?.material||p.metafields?.fabric)||'Chất liệu pha.',ambiguous:true};
 return {text:parts.map(x=>x.text).join(', '),ambiguous:false};
}
function main(){
 const source=read('clothing-200-cloudinary-vnd.json'),base=read('clothing-200-db-payload.json'),titles=read('vietnamese-titles.json'),colors=read('vietnamese-colors.json');
 assert.equal(source.length,200);assert.equal(titles.length,200);assert.equal(new Set(titles).size,200);
 const warnings=[],measurementEvidence=[];
 const payload=structuredClone(base);
 payload.categories=payload.categories.map(c=>({...c,name:c.slug==='set-do'?'Bộ đồ':c.slug==='jumpsuit'?'Đồ liền':c.name}));
 for(let index=0;index<source.length;index++){
  const s=source[index],p=payload.products[index];assert.equal(s.id,p.sourceId);
  p.name=titles[index];assert.ok([...p.name].length<=160);
  const colorValues=[...new Set(p.variants.map(v=>v.color).filter(Boolean))];
  for(const color of colorValues)if(!colors[canon(color)])throw Error('UNMAPPED_COLOR: '+color);
  p.variants=p.variants.map(v=>({...v,size:/^one size$/i.test(v.size||'')?'Một cỡ':v.size,color:v.color?colors[canon(v.color)]:null,material:material(v.material)}));
  const comp=composition(s),measure=table(s),fit=fits[s.metafields?.fit];
  if(s.metafields?.fit&&!fit)throw Error('UNMAPPED_FIT');
  const lines=[p.name+'.'];
  if(fit)lines.push('Kiểu dáng: '+fit+'.');
  lines.push('Chất liệu: '+comp.text+'.');
  if(comp.ambiguous){lines.push('Tỷ lệ thành phần chất liệu cần được xác minh lại theo nhãn sản phẩm.');warnings.push({id:p.id,reason:'SOURCE_COMPOSITION_TOTAL_AMBIGUOUS'});}
  const raw=s.description+' '+plain(s.metafields?.supplier_description||'');
  const stretchLabel=[...(s.metafields?.supplier_description||'').matchAll(/<li[^>]*>([\s\S]*?)<\/li>/gi)].map(m=>plain(m[1])).find(t=>/^stretch:/i.test(t));
  const stretch=stretchLabel||s.description;
  // Prefer explicit source labels; do not infer material performance or lining.
  if(/no stretch|non-stretch|not stretchy/i.test(stretch))lines.push('Độ co giãn: không co giãn.');
  else if(/four.way stretch/i.test(stretch))lines.push('Độ co giãn: co giãn bốn chiều.');
  else if(/highly stretch|high stretch/i.test(stretch))lines.push('Độ co giãn: co giãn tốt.');
  else if(/moderate stretch|stretch:\s*moderate/i.test(stretch))lines.push('Độ co giãn: vừa phải.');
  else if(/slightly stretch|slight stretch/i.test(stretch))lines.push('Độ co giãn: nhẹ.');
  if(/partially lined/i.test(raw))lines.push('Lớp lót: lót một phần.');
  else if(/not lined|unlined/i.test(raw))lines.push('Lớp lót: không có lớp lót.');
  else if(/fully lined|body:\s*lined|lined body/i.test(raw))lines.push('Lớp lót: có lớp lót.');
  lines.push('Tùy chọn cỡ: '+[...new Set(p.variants.map(v=>v.size).filter(Boolean))].map(v=>v==='One Size'?'Một cỡ':v).join(', ')+'.');
  lines.push('Tùy chọn màu: '+[...new Set(p.variants.map(v=>v.color).filter(Boolean))].join(', ')+'.');
  lines.push('Bảo quản: '+care(s));
  if(measure.text)lines.push(measure.text);
  else warnings.push({id:p.id,reason:'SOURCE_HAS_NO_MEASUREMENT_TABLE'});
  if(index===108)lines.push('Gợi ý phối đồ: kết hợp cùng dép đế bệt và túi cói khi đi nghỉ, hoặc giày đế xuồng và ví cầm tay khi đi ăn tối.');
  p.description=lines.join('\n\n');
  if([...p.description].length>2000)throw Error('DESCRIPTION_OVER_LIMIT: '+index+' '+[...p.description].length);
  p.images=p.images.map((image,n)=>({...image,alt:[...('Ảnh '+(n+1)+' - '+p.name)].slice(0,160).join('')}));
  measurementEvidence.push({id:p.id,table_cells:measure.cells,has_table:!!measure.text});
 }
 const report={status:'LOCAL_EDITORIAL_REWRITE_READY',products:200,variants:payload.products.reduce((n,p)=>n+p.variants.length,0),images:payload.products.reduce((n,p)=>n+p.images.length,0),measurement_tables:measurementEvidence.filter(x=>x.has_table).length,external_translation_used:false,max_description_codepoints:Math.max(...payload.products.map(x=>[...x.description].length)),warnings};
 fs.writeFileSync(path.join(dir,'clothing-200-db-payload-vi.json'),JSON.stringify(payload,null,2)+'\n');
 fs.writeFileSync(path.join(dir,'vietnamese-content-report.json'),JSON.stringify(report,null,2)+'\n');
 fs.writeFileSync(path.join(dir,'vietnamese-measurement-evidence.json'),JSON.stringify(measurementEvidence,null,2)+'\n');
 console.log(JSON.stringify(report));
}
module.exports={main,table,material,care,composition};if(require.main===module)main();
