import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { authApi } from '../api/endpoints'
import { useAuth } from '../store/AuthContext'

export default function RegisterPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ name: '', email: '', password: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  function set(field: string) {
    return (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm(f => ({ ...f, [field]: e.target.value }))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const data = await authApi.register(form.name, form.email, form.password)
      localStorage.setItem('userName', data.name || form.name)
      localStorage.setItem('userEmail', data.email || form.email)
      login(data.token, data.refreshToken, data.userId)
      navigate('/')
    } catch (err: unknown) {
      const msg = (err as {response?: {data?: {message?: string}}})?.response?.data?.message
      setError(msg || 'Registration failed')
    } finally {
      setLoading(false)
    }
  }

  const fields = [
    { id: 'reg-name', label: 'Full Name', field: 'name', type: 'text', placeholder: 'Karan Sharma' },
    { id: 'reg-email', label: 'Email', field: 'email', type: 'email', placeholder: 'karan@example.com' },
    { id: 'reg-password', label: 'Password', field: 'password', type: 'password', placeholder: '8+ characters' },
  ]

  return (
    <div className="min-h-screen flex items-center justify-center px-4"
         style={{ background: 'radial-gradient(ellipse at top, #1a2235 0%, #0a0f1e 70%)' }}>
      <div className="w-full max-w-md">
        <div className="text-center mb-10">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl mb-4"
               style={{ background: 'linear-gradient(135deg, #6366f1, #8b5cf6)' }}>
            <span className="text-2xl">⚡</span>
          </div>
          <h1 className="text-3xl font-bold text-white">Create Account</h1>
          <p className="text-sm mt-1" style={{ color: 'var(--color-muted)' }}>Join SettleUp today</p>
        </div>

        <div className="rounded-2xl p-8 border"
             style={{ background: 'var(--color-surface)', borderColor: 'var(--color-border)' }}>
          {error && (
            <div className="mb-4 p-3 rounded-lg text-sm"
                 style={{ background: '#ef444420', color: '#ef4444', border: '1px solid #ef444440' }}>
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            {fields.map(f => (
              <div key={f.field}>
                <label className="block text-sm font-medium mb-1.5" style={{ color: 'var(--color-muted)' }}>
                  {f.label}
                </label>
                <input
                  id={f.id}
                  type={f.type}
                  required
                  value={form[f.field as keyof typeof form]}
                  onChange={set(f.field)}
                  placeholder={f.placeholder}
                  className="w-full px-4 py-3 rounded-xl text-sm outline-none"
                  style={{
                    background: 'var(--color-surface-2)',
                    border: '1px solid var(--color-border)',
                    color: 'var(--color-text)',
                  }}
                />
              </div>
            ))}
            <button
              id="register-submit"
              type="submit"
              disabled={loading}
              className="w-full py-3 rounded-xl font-semibold text-sm mt-2"
              style={{
                background: loading ? 'var(--color-border)' : 'linear-gradient(135deg, #6366f1, #8b5cf6)',
                color: 'white',
                cursor: loading ? 'not-allowed' : 'pointer',
              }}>
              {loading ? 'Creating account…' : 'Get Started'}
            </button>
          </form>

          <p className="text-center text-sm mt-6" style={{ color: 'var(--color-muted)' }}>
            Already have an account?{' '}
            <Link to="/login" className="font-medium" style={{ color: 'var(--color-accent)' }}>Sign In</Link>
          </p>
        </div>
      </div>
    </div>
  )
}
