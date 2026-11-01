import { useState, useEffect, useRef } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Play, RotateCcw, ChevronRight, Volume2, FileText, Check, Mic, Square } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { FigmaAsset } from '@/components/atoms/FigmaAsset';
import { VideoLessonCard } from '@/components/molecules/VideoLessonCard';
import { Button } from '@/components/ui/button';
import { videoLessons, dictationExercises } from '@/features/listening/fixtures/videoLessons';

export default function ListeningHubPage() {
  const [params] = useSearchParams();
  const shadowing = params.get('mode') === 'shadowing';
  const library = params.get('view') === 'library' || (shadowing && params.get('view') !== 'exercise');
  const [filter, setFilter] = useState('Tất cả');
  const [index, setIndex] = useState(0);
  const [input, setInput] = useState('');
  const [checked, setChecked] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [transcript, setTranscript] = useState(true);
  const [speed, setSpeed] = useState(1);
  const [recording, setRecording] = useState(false);
  const [recordedUrl, setRecordedUrl] = useState('');
  const [error, setError] = useState('');
  const recorder = useRef(null);
  const stream = useRef(null);
  const chunks = useRef([]);
  const lesson = videoLessons.find(l => l.id === params.get('lesson')) || videoLessons[1];
  const exercise = dictationExercises[index];
  const shadowText = 'That same spirit is what makes me paint and makes your friend bake.';
  const activeText = shadowing ? shadowText : exercise.text;
  useEffect(() => () => { window.speechSynthesis?.cancel(); stream.current?.getTracks().forEach(t => t.stop()); }, []);
  useEffect(() => () => { if (recordedUrl) URL.revokeObjectURL(recordedUrl); }, [recordedUrl]);
  function play() {
    if (!('speechSynthesis' in window)) { setError('Trình duyệt này chưa hỗ trợ phát âm.'); return; }
    window.speechSynthesis.cancel();
    const speech = new SpeechSynthesisUtterance(activeText); speech.lang = 'en-US'; speech.rate = speed;
    speech.onstart = () => setPlaying(true); speech.onend = () => setPlaying(false); speech.onerror = () => setPlaying(false);
    window.speechSynthesis.speak(speech);
  }
  function next() { setIndex(i => (i+1) % dictationExercises.length); setInput(''); setChecked(false); }
  async function record() {
    if (recording) { recorder.current?.stop(); setRecording(false); return; }
    try {
      if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) throw new Error('Trình duyệt chưa hỗ trợ ghi âm.');
      stream.current = await navigator.mediaDevices.getUserMedia({ audio: true });
      chunks.current = [];
      recorder.current = new MediaRecorder(stream.current);
      recorder.current.ondataavailable = e => { if (e.data.size) chunks.current.push(e.data); };
      recorder.current.onstop = () => { setRecordedUrl(URL.createObjectURL(new Blob(chunks.current, { type: recorder.current.mimeType }))); stream.current.getTracks().forEach(t => t.stop()); };
      recorder.current.start(); setRecording(true); setError('');
    } catch (err) { setError(err.message || 'Không thể truy cập microphone.'); }
  }
  const normalized = value => value.toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, '').trim().replace(/\s+/g,' ');
  const correct = checked && normalized(input) === normalized(exercise.text);
  if (library) return <LearnerTemplate variant="video"><div className="video-library"><h1>Luyện Shadowing và dictation qua video đa dạng chủ đề</h1><div className="video-tags">{['Tất cả', 'Người mới bắt đầu', 'Nâng cao', '#movie', '#daily', '#learning', '#shadowing', '#toeic', '#entertainment', '#kids', '#songs', '#bbc', '#voa', '#toefl', '#science', '#fairy-tale', '#podcast', '#food', '#tutorial', '#news', '#vietnam', '#ted', '#travel', '#animals', '#story', '#business'].map(tag => <button type="button" className={`pill-button ${filter === tag ? 'navy' : ''}`} key={tag} onClick={() => setFilter(tag)} aria-pressed={filter === tag}>{tag}</button>)}</div>{['Movie short clip', 'Daily English Conversation'].map(group => { const list = videoLessons.filter(l => l.group === group && (filter === 'Tất cả' || filter === '#shadowing' || filter === '#learning' || (filter === '#movie' && group === 'Movie short clip') || (filter === '#daily' && group === 'Daily English Conversation') || (filter === 'Người mới bắt đầu' && l.level === 'A2') || (filter === 'Nâng cao' && l.level !== 'A2'))); return list.length > 0 && <section key={group}><div className="section-heading"><h2>{group} <small>({list.length} bài học)</small></h2></div><div className="video-lessons-grid">{list.map(l => <VideoLessonCard key={l.id} lesson={l} />)}</div></section>; })}{!videoLessons.some(l => filter === 'Tất cả' || ['#shadowing','#learning','Người mới bắt đầu','Nâng cao'].includes(filter) || (filter === '#movie' && l.group === 'Movie short clip') || (filter === '#daily' && l.group === 'Daily English Conversation')) && <p className="empty-message">Chưa có bài học trong chủ đề này.</p>}</div></LearnerTemplate>;
  return <LearnerTemplate variant="media"><h1 className="sr-only">{shadowing ? lesson.title : 'Luyện nghe chép chính tả'}</h1><div className="media-breadcrumb"><Link to="/listening?mode=shadowing">Topics</Link><ChevronRight size={12} /><span>{shadowing ? 'Movie short clip' : 'Listening Time (Shadowing)'}</span><ChevronRight size={12} /><strong>{shadowing ? lesson.title : 'AI English Listening Practice – Homes'}</strong><span className="media-level">B1</span><button className="pill-button" onClick={() => setTranscript(!transcript)}><FileText size={13} />{transcript ? 'Ẩn transcript' : 'Hiện transcript'}</button></div>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className={`media-workspace ${shadowing ? 'shadowing-workspace' : 'dictation-workspace'} ${!transcript ? 'without-transcript' : ''}`}>
      <section className={`media-player ${shadowing ? '' : 'surface'}`}><div className="media-video"><FigmaAsset screen={shadowing ? 'video-library' : 'dictation'} name={shadowing ? lesson.image : 'imgVideoCover'} alt={shadowing ? lesson.title : 'AI English Listening Practice – Homes'} /><button type="button" className="media-play-button" aria-label="Phát câu tiếng Anh" onClick={play}><Play size={28} fill="currentColor" /></button><span className="media-video-title">{shadowing ? lesson.title : 'AI English Listening Practice – Homes'}</span><span className="media-video-time">{playing ? 'Đang phát âm...' : shadowing ? '0:40 / 0:49' : '1:33 / 4:30'}</span></div>{!shadowing && <><h2>ĐIỀU KHIỂN</h2><div className="media-player-controls"><Button className="navy-button" onClick={play}><Play size={13} />BẮT ĐẦU</Button><Button onClick={play}><RotateCcw size={13} />PHÁT LẠI</Button></div><div className="listening-mascot"><FigmaAsset screen="dictation" name="imgSvg16" alt="Parroto ngồi học" /></div></>}</section>
      <section className="listening-exercise surface"><div className="playback-controls"><button className="icon-button" onClick={play} aria-label="Phát lại"><RotateCcw size={16} /></button><button className="icon-button" onClick={play} aria-label="Phát âm"><Play size={16} fill="currentColor" /></button><button className="icon-button" onClick={next} aria-label="Câu tiếp theo"><ChevronRight size={16} /></button><label className="speed-control">ϟ <select aria-label="Tốc độ phát âm" value={speed} onChange={e => setSpeed(Number(e.target.value))}><option value="0.75">0.75×</option><option value="1">1×</option><option value="1.25">1.25×</option></select></label></div>
        {shadowing ? <><p className="shadowing-sentence">{shadowText}</p><p className="shadowing-translation">Chính tinh thần đó khiến tôi vẽ tranh và khiến bạn của em nướng bánh.</p><div className="recording-controls"><Button variant="outline" onClick={play}><Volume2 size={16} />PHÁT LẠI PHÁT ÂM</Button><Button className="navy-button" onClick={record}>{recording ? <Square size={15} /> : <Mic size={15} />}{recording ? 'DỪNG GHI ÂM' : 'GHI ÂM'}</Button></div>{recordedUrl && <audio controls src={recordedUrl} className="recorded-audio" />}</> : <><label className="dictation-label" htmlFor="dictation-input">GÕ NHỮNG GÌ BẠN NGHE ĐƯỢC:</label><textarea id="dictation-input" value={input} placeholder="Nhập câu trả lời của bạn..." onChange={e => { setInput(e.target.value); setChecked(false); }} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); if (input.trim()) setChecked(true); } }} /><div className="dictation-word-hints">{checked && exercise.text.split(' ').map((w,i) => <span key={i}>{w}</span>)}</div>{checked && <div className={`dictation-feedback ${correct ? 'correct' : 'incorrect'}`} role="status"><Check size={15} />{correct ? 'CHÍNH XÁC!' : `Đáp án: ${exercise.text}`}</div>}{checked && <div className="dictation-translation"><small>BẢN DỊCH</small><p>{exercise.translation}</p></div>}<Button className="navy-button dictation-submit" disabled={!checked && !input.trim()} onClick={checked ? next : () => setChecked(true)}>{checked ? 'TIẾP THEO ›' : 'KIỂM TRA ĐÁP ÁN'}</Button></>}
      </section>
      {transcript && <aside className="transcript-panel surface"><h2>BẢN CHÉP <span>{correct ? '100%' : '0%'}</span></h2>{(shadowing ? [{ text: 'But then I start feeling like such an outsider.' },{ text: 'Frankly, I think this is a major insult.' },{ text: 'We fly with our spirit.' },{ text: shadowText },{ text: 'But we each need to find our own inspiration, Kiki.' }] : dictationExercises).map((entry,i) => <button type="button" key={i} className={i === index ? 'active' : ''} onClick={() => { if (!shadowing) { setIndex(i); setInput(''); setChecked(false); } }}><strong>#{(shadowing ? 8 : 20)+i}</strong><p>{shadowing || checked ? entry.text : '••• ••• ••• ••• •••'}</p></button>)}</aside>}
    </div>
  </LearnerTemplate>;
}
