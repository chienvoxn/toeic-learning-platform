
import { useEffect, useState } from 'react'

function App() {
  const [backendStatus, setBackendStatus] = useState('Đang kết nối...')
  const [error, setError] = useState('')

  useEffect(() => {
    fetch('/api/health')
      .then((response) => {
        if (!response.ok) {
          throw new Error('Backend không phản hồi')
        }
        return response.json()
      })
      .then((data) => {
        setBackendStatus(data.status)
      })
      .catch((err) => {
        setBackendStatus('ERROR')
        setError(err.message)
      })
  }, [])

  return (
    <div style={{
      minHeight: '100vh',
      backgroundColor: '#f4f7fc',
      fontFamily: 'Arial, sans-serif',
      padding: '60px 20px',
      textAlign: 'center'
    }}>
      <h1 style={{ color: '#2563eb' }}>
        TOEIC Learning Platform
      </h1>

      <p>Hệ thống học và luyện thi TOEIC trực tuyến</p>

      <div style={{
        backgroundColor: 'white',
        padding: '30px',
        borderRadius: '12px',
        maxWidth: '450px',
        margin: '40px auto',
        boxShadow: '0 4px 15px rgba(0,0,0,0.08)'
      }}>
        <h2>Kiểm tra kết nối hệ thống</h2>

        <p>
          Frontend React:
          <strong style={{ color: 'green' }}> Đang chạy</strong>
        </p>

        <p>
          Backend Spring Boot:
          <strong style={{
            color: backendStatus === 'UP' ? 'green' : 'red'
          }}>
            {' '}{backendStatus}
          </strong>
        </p>

        {backendStatus === 'UP' && (
          <p style={{ color: 'green' }}>
            Kết nối React và Spring Boot thành công!
          </p>
        )}

        {error && (
          <p style={{ color: 'red' }}>{error}</p>
        )}
      </div>
    </div>
  )
}

export default App
