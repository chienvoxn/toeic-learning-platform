export default function TeacherDashboardPage() {
  const modules = [
    {
      title: 'Kho học liệu',
      description: 'Quản lý bài học, tài liệu và nội dung đã biên soạn.',
      status: 'Sẵn sàng tích hợp API',
      available: true,
    },
    {
      title: 'Ngân hàng câu hỏi',
      description: 'Biên soạn câu hỏi TOEIC Listening & Reading.',
      status: 'Sẵn sàng tích hợp API',
      available: true,
    },
    {
      title: 'Đề thi & Bài nộp',
      description: 'Theo dõi đề thi và kết quả làm bài của học viên.',
      status: 'Đang hoàn thiện tích hợp',
      available: false,
    },
    {
      title: 'Lớp học & Học viên',
      description: 'Quản lý học viên và các lớp được phân công.',
      status: 'Chờ API lớp học',
      available: false,
    },
  ];

  return (
    <>
      <div className="teacher-page-heading">
        <h1>Tổng quan giảng dạy</h1>
        <p>Quản lý công việc và học liệu của giảng viên.</p>
      </div>

      <div className="teacher-grid">
        {modules.map(module => (
          <section className="teacher-card" key={module.title}>
            <h2>{module.title}</h2>
            <p>{module.description}</p>

            <span className={
              module.available
                ? 'teacher-status'
                : 'teacher-pending'
            }>
              {module.status}
            </span>
          </section>
        ))}
      </div>
    </>
  );
}
