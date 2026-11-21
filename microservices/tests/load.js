// k6 run tests/load.js (install k6 separately). No authentication required for public quiz list.
import http from 'k6/http';
import {check, sleep} from 'k6';
export const options = {
  vus: 50,
  duration: '1m',
  thresholds: {http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<500']},
};
const base = __ENV.GATEWAY_URL || 'http://localhost:8088';
export default function () {
  const r = http.get(`${base}/ms/v1/contents/quizzes`);
  check(r, {'HTTP 200': x => x.status === 200, 'returns quizzes': x => x.body.includes('Part')});
  sleep(0.5);
}
