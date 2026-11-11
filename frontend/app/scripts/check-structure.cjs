const fs = require('node:fs');
const path = require('node:path');
const { parseSync } = require('@babel/core');

const root = path.resolve(__dirname, '..');
const sourceRoot = path.join(root, 'src');
const files = [path.join(root, 'App.js'), path.join(root, 'index.js')];
function scan(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) scan(file);
    else if (/\.[cm]?jsx?$/.test(file)) files.push(file);
  }
}
scan(sourceRoot);

let checked = 0;
for (const file of files) {
  const ast = parseSync(fs.readFileSync(file, 'utf8'), { configFile: false, babelrc: false, sourceType: 'module', parserOpts: { plugins: ['jsx'] } });
  const relative = path.relative(root, file).replaceAll(path.sep, '/');
  function inspect(node) {
    if (!node || typeof node !== 'object') return;
    let specifier;
    if (/^(ImportDeclaration|ExportNamedDeclaration|ExportAllDeclaration)$/.test(node.type)) specifier = node.source?.value;
    if (node.type === 'CallExpression' && node.callee?.name === 'require') {
      if (node.arguments[0]?.type !== 'StringLiteral') throw new Error(`Dynamic require in ${relative}`);
      specifier = node.arguments[0].value;
    }
    if (specifier?.startsWith('.')) {
      const absolute = path.resolve(path.dirname(file), specifier);
      const resolved = [absolute, absolute + '.js', absolute + '.jsx', absolute + '.json', path.join(absolute, 'index.js')].find(candidate => fs.existsSync(candidate) && fs.statSync(candidate).isFile());
      if (!resolved) throw new Error(`Missing relative import: ${relative} -> ${specifier}`);
      if (!resolved.startsWith(root + path.sep)) throw new Error(`Import outside mobile: ${relative}`);
      let current = root;
      for (const part of path.relative(root, resolved).split(path.sep)) {
        if (!fs.readdirSync(current).includes(part)) throw new Error(`Import case mismatch: ${relative} -> ${specifier}`);
        current = path.join(current, part);
      }
      if (relative.startsWith('src/components/') && resolved.includes(path.sep + 'features' + path.sep)) throw new Error(`Shared UI imports a feature: ${relative}`);
      if (relative.includes('/screens/') && /\/(repositories|fixtures)\//.test(path.relative(root, resolved).replaceAll(path.sep, '/'))) throw new Error(`Screen imports data directly: ${relative}`);
      checked += 1;
    } else if (specifier?.startsWith('@/')) throw new Error(`Use relative imports in mobile: ${relative}`);
    for (const [key, value] of Object.entries(node)) {
      if (['loc', 'start', 'end', 'tokens', 'comments'].includes(key)) continue;
      if (Array.isArray(value)) value.forEach(inspect);
      else if (value && typeof value === 'object') inspect(value);
    }
  }
  inspect(ast.program);
}
console.log(`PASS: parsed ${files.length} source files; resolved ${checked} relative imports with exact filename case and mobile boundaries.`);
