import { useEffect, useRef, useState } from 'react';
import { MessageCircle, X, Send, Plus, ArrowLeft, BookOpen, Trash2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { chatApi } from './chat.api';
import './chat.css';

export default function ChatBubble() {
  const [open, setOpen] = useState(false);
  const [list, setList] = useState([]);
  const [conversation, setConversation] = useState(null);
  const [messages, setMessages] = useState([]);
  const [question, setQuestion] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [older, setOlder] = useState(false);
  const [listPage, setListPage] = useState(0);
  const [moreConversations, setMoreConversations] = useState(false);
  const [examActive, setExamActive] = useState(() => Boolean(localStorage.getItem('toeic.active.exam')));
  const panel = useRef(null), trigger = useRef(null), input = useRef(null), end = useRef(null);
  const operation = useRef(0), mounted = useRef(true), sending = useRef(false);
  const pendingRequest = useRef(null);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    const sync = () => setExamActive(Boolean(localStorage.getItem('toeic.active.exam')));
    window.addEventListener('storage', sync);
    const timer = window.setInterval(sync, 1000);
    return () => { window.removeEventListener('storage', sync); window.clearInterval(timer); };
  }, []);
  async function loadList(append = false) {
    const current = ++operation.current;
    setLoading(true); setError('');
    try {
      const next = append ? listPage + 1 : 0;
      const rows = await chatApi.conversations(undefined, next);
      if (!mounted.current || current !== operation.current) return;
      setList(value => append ? [...value, ...rows] : rows); setListPage(next); setMoreConversations(rows.length === 20);
    } catch (err) { if (mounted.current && current === operation.current) setError(err.message); }
    finally { if (mounted.current && current === operation.current) setLoading(false); }
  }
  useEffect(() => {
    if (!open) return;
    input.current?.focus();
    const key = event => {
      if (event.key === 'Escape') { setOpen(false); trigger.current?.focus(); }
      if (event.key === 'Tab') {
        const items = [...panel.current.querySelectorAll('button:not(:disabled), a[href], textarea')];
        const first = items[0], last = items.at(-1);
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      }
    };
    document.addEventListener('keydown', key);
    return () => document.removeEventListener('keydown', key);
  }, [open]);
  useEffect(() => { if (open && page === 0) end.current?.scrollIntoView({ block: 'nearest' }); }, [messages, busy, open, page]);
  async function select(item, append = false) {
    const current = ++operation.current;
    setLoading(true); setError('');
    try {
      const next = append ? page + 1 : 0;
      const rows = await chatApi.messages(item.id, undefined, next);
      if (!mounted.current || current !== operation.current) return;
      setConversation(item); setMessages(value => append ? [...rows, ...value] : rows); setPage(next); setOlder(rows.length === 50);
    } catch (err) { if (mounted.current && current === operation.current) setError(err.message); }
    finally { if (mounted.current && current === operation.current) setLoading(false); }
  }
  async function remove(item) {
    if (!window.confirm(`Xóa hội thoại “${item.title}” khỏi lịch sử của bạn?`)) return;
    const current = ++operation.current;
    setLoading(true); setError('');
    try {
      await chatApi.remove(item.id);
      if (!mounted.current || current !== operation.current) return;
      setList(value => value.filter(row => row.id !== item.id));
      if (conversation?.id === item.id) { setConversation(null); setMessages([]); setPage(0); setOlder(false); }
    } catch (err) { if (mounted.current && current === operation.current) setError(err.message); }
    finally { if (mounted.current && current === operation.current) setLoading(false); }
  }
  async function send(event) {
    event.preventDefault();
    if (examActive || sending.current || !question.trim() || loading) return;
    sending.current = true; setBusy(true); setError('');
    const text = question.trim();
    let item = conversation;
    try {
      if (!item) { item = await chatApi.create(); if (!mounted.current) return; setConversation(item); }
      if (pendingRequest.current?.conversation !== item.id || pendingRequest.current?.question !== text)
        pendingRequest.current = { conversation: item.id, question: text, id: crypto.randomUUID() };
      await chatApi.ask(item.id, text, pendingRequest.current.id);
      pendingRequest.current = null;
      if (!mounted.current) return;
      setQuestion(''); await select(item);
    } catch (err) {
      // A transport failure may hide a completed server response. Reuse its identifier on retry.
      if (err.status && err.status !== 409) pendingRequest.current = null;
      if (mounted.current) setError(`${err.message} Bạn có thể tải lại lịch sử trước khi gửi câu hỏi mới.`);
    }
    finally { sending.current = false; if (mounted.current) setBusy(false); }
  }
  return <aside className="tutor-widget">
    {open && <section className="tutor-panel" ref={panel} role="dialog" aria-label="Trợ lý TOEIC" aria-modal="true">
      <header className="tutor-header"><div className="tutor-avatar"><BookOpen size={20} /></div><div><strong>Trợ lý TOEIC</strong><small>Học cùng nguồn tham khảo</small></div>
        <button type="button" aria-label="Đóng trò chuyện" onClick={() => { setOpen(false); trigger.current?.focus(); }}><X size={20} /></button></header>
      <nav className="tutor-toolbar" aria-label="Điều hướng hội thoại">
        <button type="button" disabled={busy || loading} onClick={() => { setConversation(null); setMessages([]); setOlder(false); setPage(0); loadList(); }}><ArrowLeft size={15} /> Hội thoại</button>
        <button type="button" disabled={busy || loading} onClick={() => { setConversation(null); setMessages([]); setQuestion(''); setOlder(false); setPage(0); loadList(); input.current?.focus(); }}><Plus size={15} /> Câu hỏi mới</button>
      </nav>
      <div className="tutor-scroll">
        {!conversation && <><div className="tutor-welcome"><span>ENGLINGO · AI TUTOR</span><h2>Bạn muốn học gì hôm nay?</h2><p>Hỏi về ngữ pháp, từ vựng hoặc cách dùng tiếng Anh trong TOEIC.</p></div>
          {!list.length && !loading && <button className="tutor-suggestion" type="button" onClick={() => { setQuestion('Khi nào sử dụng thì hiện tại hoàn thành?'); input.current?.focus(); }}>Khi nào dùng thì hiện tại hoàn thành?</button>}
          {!!list.length && <div className="tutor-history"><small>Hội thoại gần đây</small>{list.map(item => <div className="tutor-history-row" key={item.id}><button className="tutor-history-open" type="button" disabled={busy || loading} onClick={() => select(item)}><MessageCircle size={16} />{item.title}</button><button className="tutor-history-delete" type="button" disabled={busy || loading} aria-label={`Xóa hội thoại ${item.title}`} onClick={() => remove(item)}><Trash2 size={16} /></button></div>)}{moreConversations && <button type="button" disabled={loading} onClick={() => loadList(true)}>Xem thêm hội thoại</button>}</div>}</>}
        {examActive && <p className="tutor-warning" role="status">AI Tutor tạm khóa trong lúc bạn đang làm bài thi.</p>}
        {older && conversation && <button type="button" className="tutor-reload" disabled={loading || busy} onClick={() => select(conversation, true)}>Xem tin nhắn trước</button>}
        <div role="log" aria-label="Tin nhắn">{messages.map(message => <article key={message.id} className={`tutor-message tutor-${message.role}`}>
          <small>{message.role === 'user' ? 'Bạn' : 'Trợ lý TOEIC'}</small><p>{message.content}</p>
          {message.answer?.warning && <div className="tutor-warning">{message.answer.warning}</div>}
          {!!message.answer?.citations?.length && <details><summary>Nguồn tham khảo ({message.answer.citations.length})</summary>{message.answer.citations.map(source => <div className="tutor-source" key={`${source.documentId}-${source.chunkId}`}><Link to={`/catalog/${source.contentId}`}>{source.title}{source.page ? ` · Trang ${source.page}` : ''}</Link><p>{source.excerpt}</p><small>Phiên bản: {source.contentVersionId.slice(0, 8)}</small></div>)}</details>}
        </article>)}</div>
        {(busy || loading) && <p className="tutor-status" role="status">{busy ? 'Đang tìm nguồn và soạn câu trả lời…' : 'Đang tải hội thoại…'}</p>}
        <div ref={end} />
      </div>
      {error && <div className="tutor-error" role="alert">{error}{conversation && <button type="button" disabled={busy || loading} onClick={() => select(conversation)}>Tải lại lịch sử</button>}</div>}
      <form className="tutor-composer" onSubmit={send}><label className="sr-only" htmlFor="tutor-question">Câu hỏi TOEIC</label><textarea id="tutor-question" ref={input} rows={2} maxLength={4000} value={question} disabled={busy} placeholder="Nhập câu hỏi của bạn…" onChange={e => setQuestion(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); e.currentTarget.form.requestSubmit(); } }} /><button type="submit" aria-label="Gửi câu hỏi" disabled={busy || loading || !question.trim()}><Send size={19} /></button><small>AI có thể mắc lỗi. Hãy đối chiếu nguồn tham khảo.</small></form>
    </section>}
    <button className="tutor-trigger" ref={trigger} type="button" aria-label={open ? 'Đóng trợ lý TOEIC' : 'Mở trợ lý TOEIC'} aria-expanded={open} onClick={() => { setOpen(value => !value); if (!open && !conversation) loadList(); }}>{open ? <X size={23} /> : <><MessageCircle size={23} /><span>Hỏi trợ lý</span></>}</button>
  </aside>;
}
