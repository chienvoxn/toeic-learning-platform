import { useEffect, useRef, useState } from 'react';
import { Play, Pause, RotateCcw, Volume2, Lock } from 'lucide-react';
import { readSession } from '@/features/auth/session';

function formatSeconds(value) {
  if (!Number.isFinite(value) || value < 0) return '0:00';
  const total = Math.floor(value);
  const mins = Math.floor(total / 60);
  const secs = total % 60;
  return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
}

async function fetchFallbackBlobUrl(fallbackUrl) {
  const token = readSession()?.accessToken;
  const response = await fetch(fallbackUrl, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) throw new Error('Không tải được file media');
  const blob = await response.blob();
  return URL.createObjectURL(blob);
}

function ImageAssetItem({ asset, label }) {
  const [src, setSrc] = useState(asset.url);
  const [triedFallback, setTriedFallback] = useState(false);

  useEffect(() => {
    setSrc(asset.url);
    setTriedFallback(false);
  }, [asset.url]);

  useEffect(() => () => {
    if (src && src.startsWith('blob:')) URL.revokeObjectURL(src);
  }, [src]);

  async function handleError() {
    if (triedFallback || !asset.fallbackUrl) return;
    setTriedFallback(true);
    try {
      const blobUrl = await fetchFallbackBlobUrl(asset.fallbackUrl);
      setSrc(blobUrl);
    } catch {
      // Keep current state if fallback also fails
    }
  }

  return (
    <figure className="listening-image-box my-3">
      <img
        src={src}
        alt={label || 'Hình minh họa câu hỏi TOEIC Listening'}
        onError={handleError}
        className="max-w-full rounded-lg border border-slate-200 shadow-sm"
        style={{ maxHeight: '280px', objectFit: 'contain' }}
      />
    </figure>
  );
}

function AudioAssetItem({ asset, strictExamMode = false, disabled = false }) {
  const audioRef = useRef(null);
  const [src, setSrc] = useState(asset.url);
  const [triedFallback, setTriedFallback] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(asset.durationSeconds || 0);
  const [playCount, setPlayCount] = useState(0);
  const [completedOnce, setCompletedOnce] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [error, setError] = useState('');

  useEffect(() => {
    setSrc(asset.url);
    setTriedFallback(false);
    setPlaying(false);
    setCurrentTime(0);
    setPlayCount(0);
    setCompletedOnce(false);
    setError('');
  }, [asset.url]);

  useEffect(() => () => {
    if (src && src.startsWith('blob:')) URL.revokeObjectURL(src);
  }, [src]);

  async function handleAudioError() {
    if (!triedFallback && asset.fallbackUrl) {
      setTriedFallback(true);
      try {
        const blobUrl = await fetchFallbackBlobUrl(asset.fallbackUrl);
        setSrc(blobUrl);
        setError('');
        return;
      } catch {
        // Fallback failed
      }
    }
    setPlaying(false);
    setError('Không thể phát file audio này.');
  }

  async function togglePlay() {
    const el = audioRef.current;
    if (!el || disabled) return;
    if (playing) {
      if (strictExamMode) return; // Phòng thi thật không cho tạm dừng/tua lại khi đang phát
      el.pause();
      setPlaying(false);
      return;
    }
    if (strictExamMode && completedOnce) return;
    try {
      setError('');
      el.playbackRate = strictExamMode ? 1 : speed;
      await el.play();
      setPlaying(true);
      setPlayCount(c => c + 1);
    } catch {
      await handleAudioError();
    }
  }

  function handleSeek(event) {
    if (strictExamMode || disabled || !audioRef.current) return;
    const nextTime = Number(event.target.value);
    audioRef.current.currentTime = nextTime;
    setCurrentTime(nextTime);
  }

  function handleReplay() {
    if (strictExamMode || disabled || !audioRef.current) return;
    audioRef.current.currentTime = 0;
    setCurrentTime(0);
    audioRef.current.play().then(() => setPlaying(true)).catch(() => {});
  }

  const effectiveDuration = duration > 0 ? duration : (asset.durationSeconds || 0);
  const lockedByStrictMode = strictExamMode && completedOnce;

  return (
    <div className="listening-audio-player rounded-lg border border-slate-200 bg-slate-50 p-3 my-3" data-testid="listening-audio-player">
      <audio
        ref={audioRef}
        src={src}
        preload="metadata"
        onLoadedMetadata={e => {
          if (Number.isFinite(e.currentTarget.duration) && e.currentTarget.duration > 0) {
            setDuration(e.currentTarget.duration);
          }
        }}
        onTimeUpdate={e => setCurrentTime(e.currentTarget.currentTime)}
        onEnded={() => {
          setPlaying(false);
          setCompletedOnce(true);
        }}
        onPause={() => setPlaying(false)}
        onPlay={() => setPlaying(true)}
        onError={handleAudioError}
      />
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          className="pill-button navy flex items-center gap-1"
          disabled={disabled || lockedByStrictMode || (strictExamMode && playing)}
          onClick={togglePlay}
          aria-label={playing ? 'Tạm dừng audio' : 'Phát audio'}
        >
          {playing ? <Pause size={15} /> : <Play size={15} />}
          <span>{lockedByStrictMode ? 'Đã phát (1/1)' : playing ? 'Đang phát' : 'Phát audio'}</span>
        </button>

        {!strictExamMode && (
          <button
            type="button"
            className="pill-button flex items-center gap-1"
            disabled={disabled}
            onClick={handleReplay}
            aria-label="Phát lại từ đầu"
          >
            <RotateCcw size={14} />
            <span>Nghe lại</span>
          </button>
        )}

        <div className="flex-1 min-w-[160px] flex items-center gap-2">
          <Volume2 size={16} className="text-slate-600" />
          <input
            type="range"
            min={0}
            max={effectiveDuration || 1}
            step={0.1}
            value={Math.min(currentTime, effectiveDuration || 1)}
            disabled={disabled || strictExamMode}
            onChange={handleSeek}
            aria-label="Thanh tiến trình audio"
            className="w-full"
          />
          <span className="text-xs font-mono text-slate-600 whitespace-nowrap">
            {formatSeconds(currentTime)} / {formatSeconds(effectiveDuration)}
          </span>
        </div>

        {!strictExamMode ? (
          <label className="text-xs text-slate-600 flex items-center gap-1">
            <span>Tốc độ</span>
            <select
              aria-label="Tốc độ phát audio"
              value={speed}
              disabled={disabled}
              onChange={e => {
                const next = Number(e.target.value);
                setSpeed(next);
                if (audioRef.current) audioRef.current.playbackRate = next;
              }}
              className="border rounded px-1 py-0.5 bg-white"
            >
              <option value={0.75}>0.75×</option>
              <option value={1}>1×</option>
              <option value={1.25}>1.25×</option>
            </select>
          </label>
        ) : (
          <span className="text-xs text-amber-700 flex items-center gap-1" title="Chế độ thi thật: khóa tua và chỉ phát 1 lần">
            <Lock size={13} />
            <span>Chế độ phòng thi (1 lần · khóa tua) ({playCount}/1)</span>
          </span>
        )}
      </div>
      {error && <p className="text-xs text-red-600 mt-1" role="alert">{error}</p>}
    </div>
  );
}

export function ListeningMediaPlayer({
  audioAssets = [],
  imageAssets = [],
  strictExamMode = false,
  disabled = false,
  label = '',
}) {
  if (!audioAssets?.length && !imageAssets?.length) return null;
  return (
    <div className="listening-media-container my-2">
      {imageAssets?.map(asset => (
        <ImageAssetItem key={asset.resourceId} asset={asset} label={label} />
      ))}
      {audioAssets?.map(asset => (
        <AudioAssetItem
          key={asset.resourceId}
          asset={asset}
          strictExamMode={strictExamMode}
          disabled={disabled}
        />
      ))}
    </div>
  );
}
