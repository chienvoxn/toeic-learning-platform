export default function ExamScoreSummary({ result }) {
  const hasScore = (result.estimatedTotalScore ?? result.totalScore) != null || (result.estimatedReadingScore ?? result.estimatedListeningScore) != null;
  return <section className="score-summary surface">
    <div className="score-circle"><strong>{hasScore ? (result.estimatedTotalScore ?? result.totalScore) ?? (result.estimatedReadingScore ?? result.estimatedListeningScore) : result.rawCorrect}</strong><span>/{hasScore ? (result.estimatedTotalScore ?? result.totalScore) != null ? 990 : 495 : result.totalQuestions}</span></div>
    <h2>{hasScore ? 'Điểm TOEIC ước tính' : 'Số câu trả lời đúng'}</h2>
    {!hasScore && <p>Độ chính xác: {result.accuracyRate}% · Không quy đổi điểm TOEIC.</p>}
    <p className="score-submitted">Hoàn thành: {new Date(result.submittedAt).toLocaleString('vi-VN')}</p>
  </section>;
}
