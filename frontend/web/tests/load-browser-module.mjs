import { readFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../src/', import.meta.url));
const cache = new Map();
// Load the production adapters without a browser or a second implementation.
export async function loadBrowserModule(relativePath) {
  return import(await moduleUrl(resolve(root, relativePath)));
}
async function moduleUrl(path) {
  if (cache.has(path)) return cache.get(path);
  let code = await readFile(path, 'utf8');
  const imports = [...code.matchAll(/from\s+['"]([^'"]+)['"]/g)];
  for (const match of imports) {
    const name = match[1];
    const dependency = name.startsWith('@/') ? resolve(root, name.slice(2)) : resolve(dirname(path), name);
    const url = await moduleUrl(dependency.endsWith('.js') ? dependency : `${dependency}.js`);
    code = code.replace(match[0], `from '${url}'`);
  }
  code = code.replaceAll('import.meta.env.VITE_API_BASE_URL', 'undefined');
  const url = `data:text/javascript;base64,${Buffer.from(code).toString('base64')}`;
  cache.set(path, url);
  return url;
}
