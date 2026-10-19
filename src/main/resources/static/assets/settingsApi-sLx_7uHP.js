async function a(r,s){try{const t=await r.text(),e=JSON.parse(t);if(typeof e?.message=="string"&&e.message.trim())return e.message;if(t.trim())return t}catch{}return s}export{a as p};
