import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { groupsApi } from '../api/endpoints';
import { useAuth } from '../store/AuthContext';
import Navbar from '../components/Navbar';

function GroupCard({ group }: { group: any }) {
  const navigate = useNavigate();
  const { userId } = useAuth();
  const groupId = group.groupId || group.id;
  const currency = group.defaultCurrency || group.currency || 'INR';
  
  const { data: balances } = useQuery({
    queryKey: ['balances', groupId],
    queryFn: () => groupsApi.getBalances(groupId),
  });

  const balancesList = Array.isArray(balances) ? balances : (balances?.balances || []);
  const myBalanceEntry = balancesList.find((b: any) => b.userId === userId);
  const myBalance = myBalanceEntry ? parseFloat(myBalanceEntry.netBalance || myBalanceEntry.balance || '0') : 0;
  
  let balanceColor = 'var(--color-muted)';
  let balanceText = 'Settled up';
  if (myBalance > 0) {
    balanceColor = 'var(--color-success)';
    balanceText = `You are owed ${currency} ${myBalance.toFixed(2)}`;
  } else if (myBalance < 0) {
    balanceColor = 'var(--color-danger)';
    balanceText = `You owe ${currency} ${Math.abs(myBalance).toFixed(2)}`;
  }

  return (
    <div 
      className="p-5 rounded-2xl cursor-pointer transition-all duration-200 hover:-translate-y-1"
      style={{ 
        background: 'var(--color-surface)', 
        border: '1px solid var(--color-border)',
        boxShadow: '0 4px 6px rgba(0,0,0,0.1)'
      }}
      onClick={() => navigate(`/groups/${groupId}`)}
    >
      <div className="flex justify-between items-start mb-3">
        <h3 className="text-lg font-semibold">{group.name}</h3>
        <span 
          className="text-xs px-2 py-1 rounded-full font-medium"
          style={{ background: 'var(--color-surface-2)', color: 'var(--color-muted)' }}
        >
          {group.members?.length || 0} members
        </span>
      </div>
      <p className="text-sm mb-4" style={{ color: 'var(--color-muted)' }}>
        {group.description || 'No description'}
      </p>
      <div 
        className="text-sm font-medium px-3 py-2 rounded-xl"
        style={{ color: balanceColor, background: 'var(--color-surface-2)' }}
      >
        {balanceText}
      </div>
    </div>
  );
}

export default function GroupListPage() {
  const queryClient = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [formData, setFormData] = useState({ name: '', description: '', currency: 'INR', budgetAmount: '' });

  const { data: groups, isLoading } = useQuery({
    queryKey: ['groups'],
    queryFn: groupsApi.list,
  });

  const createGroup = useMutation({
    mutationFn: (data: any) => groupsApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groups'] });
      setShowModal(false);
      setFormData({ name: '', description: '', currency: 'INR', budgetAmount: '' });
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    createGroup.mutate({
      ...formData,
      budgetAmount: formData.budgetAmount ? Number(formData.budgetAmount) : undefined,
    });
  };

  return (
    <div className="min-h-screen">
      <Navbar />
      
      <main className="max-w-5xl mx-auto px-6 py-8">
        <div className="flex justify-between items-center mb-8">
          <h1 className="text-3xl font-bold">My Groups</h1>
          <button 
            onClick={() => setShowModal(true)}
            className="px-5 py-2.5 rounded-xl text-sm font-semibold transition-all duration-200"
            style={{ background: 'linear-gradient(135deg, var(--color-accent), var(--color-accent-hover))', color: '#fff' }}
          >
            + New Group
          </button>
        </div>

        {isLoading ? (
          <div className="text-center py-10" style={{ color: 'var(--color-muted)' }}>Loading groups...</div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {groups?.map((group: any) => (
              <GroupCard key={group.groupId || group.id} group={group} />
            ))}
            {(!groups || groups.length === 0) && (
              <div className="col-span-full text-center py-10" style={{ color: 'var(--color-muted)' }}>
                You aren't part of any groups yet. Create one to get started!
              </div>
            )}
          </div>
        )}
      </main>

      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div 
            className="w-full max-w-md p-6 rounded-2xl"
            style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}
          >
            <h2 className="text-xl font-bold mb-4">Create New Group</h2>
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
              <div>
                <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Name</label>
                <input 
                  required
                  type="text"
                  className="w-full px-4 py-2.5 rounded-xl outline-none"
                  style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
                  value={formData.name}
                  onChange={(e) => setFormData({...formData, name: e.target.value})}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Description (optional)</label>
                <input 
                  type="text"
                  className="w-full px-4 py-2.5 rounded-xl outline-none"
                  style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
                  value={formData.description}
                  onChange={(e) => setFormData({...formData, description: e.target.value})}
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Currency</label>
                  <input 
                    required
                    type="text"
                    className="w-full px-4 py-2.5 rounded-xl outline-none"
                    style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
                    value={formData.currency}
                    onChange={(e) => setFormData({...formData, currency: e.target.value})}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Budget (optional)</label>
                  <input 
                    type="number"
                    className="w-full px-4 py-2.5 rounded-xl outline-none"
                    style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
                    value={formData.budgetAmount}
                    onChange={(e) => setFormData({...formData, budgetAmount: e.target.value})}
                  />
                </div>
              </div>
              
              <div className="flex justify-end gap-3 mt-4">
                <button 
                  type="button" 
                  onClick={() => setShowModal(false)}
                  className="px-4 py-2 rounded-xl text-sm font-medium"
                  style={{ background: 'var(--color-surface-2)', color: 'var(--color-text)' }}
                >
                  Cancel
                </button>
                <button 
                  type="submit"
                  disabled={createGroup.isPending}
                  className="px-4 py-2 rounded-xl text-sm font-semibold disabled:opacity-50"
                  style={{ background: 'var(--color-accent)', color: '#fff' }}
                >
                  {createGroup.isPending ? 'Creating...' : 'Create Group'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
