// 后端行为自测用例（期望值全部来自对原站 name.vurls.cn 的实测）
const cases = [
  // ---- 编码方向 ----
  ['E864',   'Unicode',    { gb18030: '82 35 91 34', gbk: 'FE A0',      ibm1388: 'CE A6' }],
  ['1E3F',   'Unicode',    { gb18030: 'A8 BC',       gbk: '[U+1E3F]',   ibm1388: '46 5C' }],
  ['9FB4',   'Unicode',    { gb18030: 'FE 59',       gbk: '[U+9FB4]' }],
  ['E7C7',   'Unicode',    { gb18030: '81 35 F4 37', gbk: 'A8 BC',      ibm1388: '46 5C' }],
  ['FE10',   'Unicode',    { gb18030: 'A6 D9',       gbk: '[U+FE10]' }],
  ['E81E',   'Unicode',    { gb18030: '82 35 90 37', gbk: 'FE 59',      ibm1388: 'CE 5F' }],
  ['FE19',   'Unicode',    { gb18030: 'A6 F3' }],
  ['20BB7',  'Unicode',    { gb18030: '95 34 B2 35', gbk: '[U+20BB7]',  ibm1388: '[U+20BB7]' }],
  ['4E00',   'Unicode',    { gb18030: 'D2 BB',       gbk: 'D2 BB',      ibm1388: '59 BA' }],
  ['95EB',   'Unicode',    { gb18030: 'E3 C6',       gbk: 'E3 C6',      ibm1388: '62 66' }],
  ['9F99',   'Unicode',    { gb18030: 'C1 FA',       gbk: 'C1 FA',      ibm1388: '51 9B' }],
  ['20AC',   'Unicode',    { gb18030: 'A2 E3',       gbk: '80',         ibm1388: '45 EB' }],
  ['41',     'char',       { unicodeHex: '0034',      gbk: '34',         ibm1388: 'F4' }],
  ['闫',      'char',       { unicodeHex: '95EB',      utf8: 'E9 97 AB', gb18030: 'E3 C6', gbk: 'E3 C6', ibm1388: '62 66' }],
  ['龙天',    'char',       { unicodeHex: '9F99',      gbk: 'C1 FA' }],
  // ---- 解码方向 ----
  ['FEA0',    'GB18030',   { unicodeHex: '9FBB' }],
  ['FE59',    'GB18030',   { unicodeHex: '9FB4' }],
  ['82359037','GB18030',   { unicodeHex: 'E81E' }],
  ['82359134','GB18030',   { unicodeHex: 'E864' }],
  ['8135F437','GB18030',   { unicodeHex: 'E7C7' }],
  ['A8BC',    'GB18030',   { unicodeHex: '1E3F' }],
  ['A6D9',    'GB18030',   { unicodeHex: 'FE10' }],
  ['A6F3',    'GB18030',   { unicodeHex: 'FE19' }],
  ['D2BB',    'GB18030',   { unicodeHex: '4E00' }],
  ['9534B235','GB18030',   { unicodeHex: '20BB7', utf8: 'F0 A0 AE B7' }],
  ['80',      'GB18030',   { found: 'false' }],
  ['80',      'GBK',       { unicodeHex: '20AC' }],
  ['E3C6',    'GBK',       { unicodeHex: '95EB' }],
  ['FE51',    'GBK',       { unicodeHex: 'E816' }],
  ['D92EE',   'GBK',       { unicodeHex: '' }],
  ['E4B880',  'UTF-8',     { unicodeHex: '4E00' }],
  ['F0A0AEB7','UTF-8',     { unicodeHex: '20BB7' }],
  ['D842DFB7','utf_16_be', { unicodeHex: '20BB7' }],
  ['004E',    'utf_16_be', { unicodeHex: '004E' }],
  ['59BA',    'IBM1388',   { unicodeHex: '4E00' }],
  ['6266',    'IBM1388',   { unicodeHex: '95EB' }],
  ['CF41',    'IBM1388',   { unicodeHex: '3400' }],
  ['45EB',    'IBM1388',   { unicodeHex: '20AC' }],
  ['4E4E00',  'Unicode',   { unicodeHex: '' }],
  ['ZZZZ',    'Unicode',   { unicodeHex: '' }],
  ['',        'char',      { unicodeHex: '' }],
];

const base = 'http://localhost:10030/api/uchar/query';
let pass = 0, fail = 0;
for (const [input, searchType, exp] of cases) {
  const r = await (await fetch(base, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ input, searchType }),
  })).json();
  const diffs = [];
  for (const [k, v] of Object.entries(exp)) {
    const got = (k === 'found') ? String(r.found) : r[k];
    if (got !== v) diffs.push(`${k}: got=${JSON.stringify(got)} want=${JSON.stringify(v)}`);
  }
  if (diffs.length) {
    fail++;
    console.log(`FAIL [${input}] (${searchType}) -> ${diffs.join(' ; ')}`);
  } else {
    pass++;
  }
}
console.log(`\nPASS=${pass} FAIL=${fail}`);
process.exit(fail ? 1 : 0);
