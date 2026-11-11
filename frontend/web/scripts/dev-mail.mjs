// Hộp thư SMTP chỉ dùng local, không chuyển tiếp thư ra Internet.
import net from 'node:net';
import http from 'node:http';
import { randomUUID } from 'node:crypto';

const messages = [];
const escape = value => String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
const smtp = net.createServer(socket => {
  socket.setEncoding('utf8');
  socket.setTimeout(60000, () => socket.destroy());
  socket.on('error', () => {});
  let buffer = '', inData = false, lines = [], recipients = [], sender = '', size = 0;
  const reply = text => socket.write(`${text}\r\n`);
  reply('220 localhost TOEIC development inbox');
  socket.on('data', chunk => {
    buffer += chunk;
    if (buffer.length > 1024 * 1024) { socket.destroy(); return; }
    let newline;
    while ((newline = buffer.indexOf('\r\n')) >= 0) {
      const line = buffer.slice(0, newline); buffer = buffer.slice(newline + 2);
      if (inData) {
        if (line === '.') {
          messages.unshift({ id: randomUUID(), recipients: [...recipients], sender, receivedAt: new Date().toISOString(), raw: lines.join('\r\n') });
          messages.splice(30); inData = false; lines = []; size = 0; recipients = []; sender = '';
          reply('250 Message stored locally');
        } else {
          size += line.length;
          if (size > 1024 * 1024) { socket.destroy(); return; }
          lines.push(line.startsWith('..') ? line.slice(1) : line);
        }
        continue;
      }
      if (/^(EHLO|HELO)\b/i.test(line)) reply('250 localhost');
      else if (/^MAIL FROM:/i.test(line)) { sender = line.slice(10).trim(); recipients = []; reply('250 OK'); }
      else if (/^RCPT TO:/i.test(line)) { recipients.push(line.slice(8).trim()); reply('250 OK'); }
      else if (/^DATA$/i.test(line) && recipients.length) { inData = true; reply('354 End with a dot on its own line'); }
      else if (/^QUIT$/i.test(line)) { socket.end('221 Bye\r\n'); }
      else if (/^RSET$/i.test(line)) { recipients = []; lines = []; sender = ''; reply('250 OK'); }
      else if (/^NOOP$/i.test(line)) reply('250 OK');
      else reply('502 Command unavailable in development inbox');
    }
  });
});
const inbox = http.createServer((request, response) => {
  const allowedHosts = new Set(['localhost:8025', '127.0.0.1:8025']);
  if (!allowedHosts.has(request.headers.host) || request.method !== 'GET') { response.writeHead(403); response.end(); return; }
  response.setHeader('Cache-Control', 'no-store');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  response.setHeader('Content-Security-Policy', "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'");
  if (request.url === '/api/messages') {
    response.setHeader('Content-Type', 'application/json; charset=utf-8');
    response.end(JSON.stringify(messages)); return;
  }
  if (request.url !== '/') { response.writeHead(404); response.end(); return; }
  response.setHeader('Content-Type', 'text/html; charset=utf-8');
  response.end(`<!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>TOEIC · Hộp thư local</title><style>body{font:16px system-ui;background:#f4f7fc;color:#25314a;max-width:900px;margin:40px auto;padding:20px}article{background:white;border:1px solid #dde5f0;border-radius:16px;margin:20px 0;padding:24px}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:14px/1.8 monospace}a{color:#146bb2}</style><h1>Hộp thư kiểm thử TOEIC</h1><p>Email chỉ lưu trong bộ nhớ của máy này. Không gửi ra ngoài Internet.</p><p><a href="/">Làm mới hộp thư</a> · <a href="http://localhost:5173/forgot-password">Mở trang quên mật khẩu</a></p>${messages.length ? messages.map(mail => `<article><h2>${escape(mail.recipients.join(', '))}</h2><p>${escape(mail.receivedAt)}</p><pre>${escape(mail.raw)}</pre></article>`).join('') : '<p>Chưa có email. Yêu cầu đặt lại mật khẩu rồi làm mới trang này để xem OTP.</p>'}</html>`);
});
for (const server of [smtp, inbox]) server.on('error', error => { console.error(`Không mở được hộp thư local: ${error.code}`); process.exit(1); });
smtp.listen(1025, '127.0.0.1', () => console.log('SMTP local sẵn sàng tại 127.0.0.1:1025'));
inbox.listen(8025, '127.0.0.1', () => console.log('Hộp thư local: http://localhost:8025'));
function shutdown() { smtp.close(); inbox.close(); process.exit(0); }
process.on('SIGINT', shutdown); process.on('SIGTERM', shutdown);
