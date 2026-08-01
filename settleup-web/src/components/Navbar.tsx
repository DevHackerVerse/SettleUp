import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../store/AuthContext';
import { notificationsApi, groupsApi, usersApi } from '../api/endpoints';

export default function Navbar() {
  const { logout, userId } = useAuth();
  const navigate = useNavigate();
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [copied, setCopied] = useState(false);

  const { data: notifications } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => notificationsApi.list(true),
  });

  const { data: groups } = useQuery({
    queryKey: ['groups'],
    queryFn: () => groupsApi.list(),
  });

  const { data: me } = useQuery({
    queryKey: ['users', 'me'],
    queryFn: () => usersApi.getMe(),
    enabled: !!userId,
  });

  let userName = me?.name || localStorage.getItem('userName') || 'SettleUp User';
  let userEmail = me?.email || localStorage.getItem('userEmail') || 'user@settleup.com';

  if (me?.name) localStorage.setItem('userName', me.name);
  if (me?.email) localStorage.setItem('userEmail', me.email);

  if (groups && Array.isArray(groups)) {
    for (const g of groups) {
      const member = g.members?.find((m: any) => (m.userId || m.id) === userId);
      if (member) {
        if (member.name && userName === 'SettleUp User') {
          userName = member.name;
          localStorage.setItem('userName', member.name);
        }
        if (member.email && userEmail === 'user@settleup.com') {
          userEmail = member.email;
          localStorage.setItem('userEmail', member.email);
        }
        break;
      }
    }
  }

  const unreadCount = notifications?.length || 0;

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const copyUserId = () => {
    if (userId) {
      navigator.clipboard.writeText(userId);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <>
      <nav
        className="flex items-center justify-between px-6"
        style={{
          background: 'var(--color-surface)',
          borderBottom: '1px solid var(--color-border)',
          height: '64px',
        }}
      >
        <div 
          className="flex items-center gap-2 cursor-pointer" 
          onClick={() => navigate('/')}
        >
          <span className="text-xl font-bold" style={{ color: 'var(--color-accent)' }}>⚡ SettleUp</span>
        </div>

        <div className="flex items-center gap-6">
          <div className="relative cursor-pointer">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ color: 'var(--color-muted)' }}>
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
              <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
            </svg>
            {unreadCount > 0 && (
              <span 
                className="absolute -top-1 -right-1 flex items-center justify-center text-xs font-bold rounded-full h-4 w-4"
                style={{ background: 'var(--color-danger)', color: '#fff' }}
              >
                {unreadCount}
              </span>
            )}
          </div>

          <div 
            onClick={() => setShowProfileModal(true)}
            className="flex items-center justify-center rounded-full h-9 w-9 text-sm font-bold cursor-pointer transition-transform hover:scale-105 shadow-sm"
            style={{ background: 'var(--color-accent)', color: '#fff' }}
            title="View Profile"
          >
            {userName.substring(0, 2).toUpperCase() || userId?.substring(0, 2).toUpperCase() || 'U'}
          </div>

          <button 
            onClick={handleLogout}
            className="text-sm font-medium px-4 py-2 rounded-xl transition-all duration-200 hover:opacity-80"
            style={{ background: 'var(--color-surface-2)', color: 'var(--color-text)' }}
          >
            Logout
          </button>
        </div>
      </nav>

      {showProfileModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div
            className="w-full max-w-sm p-6 rounded-2xl shadow-2xl relative transition-all"
            style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}
          >
            <button
              onClick={() => setShowProfileModal(false)}
              className="absolute top-4 right-4 text-sm font-bold p-1 rounded-lg"
              style={{ color: 'var(--color-muted)' }}
            >
              ✕
            </button>

            <div className="flex flex-col items-center text-center mt-2 mb-6">
              <div
                className="w-20 h-20 rounded-full flex items-center justify-center text-2xl font-extrabold mb-4 shadow-md"
                style={{ background: 'var(--color-accent)', color: '#fff' }}
              >
                {userName.substring(0, 2).toUpperCase() || 'U'}
              </div>
              <h3 className="text-xl font-bold">{userName}</h3>
              <p className="text-sm mt-1" style={{ color: 'var(--color-muted)' }}>{userEmail}</p>
            </div>

            <div
              className="p-4 rounded-xl space-y-3 mb-6 text-left"
              style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)' }}
            >
              <div>
                <span className="text-xs font-bold uppercase block mb-1" style={{ color: 'var(--color-muted)' }}>
                  User ID
                </span>
                <div className="flex items-center justify-between gap-2">
                  <span className="text-xs font-mono truncate select-all">{userId}</span>
                  <button
                    onClick={copyUserId}
                    className="text-xs font-semibold px-2.5 py-1 rounded-lg shrink-0 transition-colors"
                    style={{ background: 'var(--color-surface-2)', color: copied ? 'var(--color-success)' : 'var(--color-text)' }}
                  >
                    {copied ? '✓ Copied' : 'Copy'}
                  </button>
                </div>
              </div>

              <div className="pt-2 border-t flex justify-between items-center" style={{ borderColor: 'var(--color-border)' }}>
                <span className="text-xs font-bold uppercase" style={{ color: 'var(--color-muted)' }}>
                  Active Groups
                </span>
                <span className="text-sm font-bold">{groups?.length || 0} groups</span>
              </div>
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => setShowProfileModal(false)}
                className="flex-1 py-2.5 rounded-xl font-semibold text-sm"
                style={{ background: 'var(--color-surface-2)', color: 'var(--color-text)' }}
              >
                Close
              </button>
              <button
                onClick={() => {
                  setShowProfileModal(false);
                  handleLogout();
                }}
                className="flex-1 py-2.5 rounded-xl font-semibold text-sm transition-opacity"
                style={{ background: 'rgba(239, 68, 68, 0.15)', color: 'var(--color-danger)' }}
              >
                Logout
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
