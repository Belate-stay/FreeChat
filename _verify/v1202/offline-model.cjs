// Isolated offline model fixture; no real API keys or external model calls.
const http = require('node:http');
http.createServer((req, res) => {
  let body = '';
  req.on('data', chunk => body += chunk);
  req.on('end', () => {
    const input = JSON.parse(body || '{}');
    console.log(JSON.stringify({ path: req.url, messages: input.messages?.length, stream: input.stream }));
    setTimeout(() => {
      res.writeHead(200, {'Content-Type': 'application/json'});
      res.end(JSON.stringify({id:'offline-qa', choices:[{message:{role:'assistant', content:'保持自然温和的人设，忠于已保存的角色资料和当前用户输入。'}}]}));
    }, 15000);
  });
}).listen(18764, '127.0.0.1');
