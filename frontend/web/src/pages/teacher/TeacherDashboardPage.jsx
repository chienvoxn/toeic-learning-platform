export default function TeacherDashboardPage() {
  const modules = [
    {
      title: 'Kho học liệu',
      description: 'Quản lý bài học, tài liệu và nội dung đã biên soạn.',
      status: 'Đọc và biên soạn qua máy chủ',
      available: true,
    },
    {
      title: 'Ngân hàng câu hỏi',
      description: 'Biên soạn câu hỏi TOEIC Listening & Reading.',
      status: 'Câu hỏi / nhóm và phiên bản',
      available: true,
    },
    {
      title: 'Bộ đề và phiên bản',
      description: 'Biên soạn DRAFT, đọc phiên bản và thao tác theo quyền.',
      status: 'DRAFT đã nối; Full Mock chờ blueprint / media',
      available: true,
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
