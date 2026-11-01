export default function ExamTemplate({ header, content, sidebar }) {
  return <div className="exam-shell"><header className="exam-header">{header}</header><div className="exam-body"><main className="exam-content">{content}</main><aside className="exam-navigator">{sidebar}</aside></div></div>;
}
