import { createContext, useContext, useState, useCallback } from 'react'
import type { ReactNode } from 'react'

interface AuthState {
  token: string | null
  userId: string | null
}

interface AuthContextType extends AuthState {
  login: (token: string, refreshToken: string, userId: string) => void
  logout: () => void
  isAuthenticated: boolean
}

const AuthContext = createContext<AuthContextType | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    token: localStorage.getItem('token'),
    userId: localStorage.getItem('userId'),
  })

  const login = useCallback((token: string, refreshToken: string, userId: string) => {
    localStorage.setItem('token', token)
    localStorage.setItem('refreshToken', refreshToken)
    localStorage.setItem('userId', userId)
    setState({ token, userId })
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userId')
    setState({ token: null, userId: null })
  }, [])

  return (
    <AuthContext.Provider value={{ ...state, login, logout, isAuthenticated: !!state.token }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
