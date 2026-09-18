// Dependency-free check of the UX prototype's generation rules, not Android tests.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(process.argv[2] || require('node:path').join(__dirname, '../design/lotto-pocket.html'), 'utf8');
const scripts = [...source.matchAll(/<script>([\s\S]*?)<\/script>/g)];
assert.equal(scripts.length, 1);
const elements = new Map();
const element = selector => {
  if (!elements.has(selector)) elements.set(selector, {innerHTML:'',textContent:'',value:'',style:{},classList:{toggle(){}},setAttribute(){},removeAttribute(){}});
  return elements.get(selector);
};
const root = {dataset:{},querySelector:element,querySelectorAll:()=>[],addEventListener(){}};
const context = vm.createContext({document:{getElementById:()=>root},console});
vm.runInContext(scripts[0][1].replace('const design={', 'globalThis.check={state,pool,generate,makeGame,parse,key,sample};const design={'), context);
const {state,pool,generate,makeGame,parse,key,sample}=context.check;
state.tickets=sample;
assert.equal(pool().excluded.size,21);
assert.equal(pool().values.length,24);
for(let count=1;count<=5;count++){
  state.count=count;state.games=[];state.locks=[];generate();
  assert.equal(state.games.length,count);
  assert.equal(new Set(state.games.map(key)).size,count);
  for(const g of state.games){assert.equal(new Set(g).size,6);assert(g.every(n=>n>=1&&n<=45&&!pool().excluded.has(n)));}
}
const kept=state.games[0].slice(0,2), before=key(state.games[0]);
state.locks[0]=kept;
const rerolled=makeGame(0,state.games.slice(1),state.games[0]);
assert(kept.every(n=>rerolled.includes(n)));assert.notEqual(key(rerolled),before);
state.locks[0]=[...state.games[0]];generate();assert.equal(key(state.games[0]),before);
state.hot=true;assert.equal(pool().restored.length,10);assert.equal(pool().values.length,34);
state.mode='registered';assert.equal(pool().restored.length,3);assert.equal(pool().values.length,27);
assert.equal(parse('1 2 3 4 5 6\n7,8,9,10,11,12').length,2);
for(const bad of ['', '1 1 2 3 4 5','0 1 2 3 4 5','1 2 3 4 5 46','1 2 3 4 5 6.5','1 2 3 4 5','1 2 3 4 5 6\n'.repeat(6)])assert.throws(()=>parse(bad));
console.log('PASS: 1–5 games, exclusion, uniqueness, locking, reroll, restoration modes, manual validation.');
