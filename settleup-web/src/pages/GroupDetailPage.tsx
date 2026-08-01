import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { groupsApi, expensesApi, settlementsApi, usersApi } from '../api/endpoints';
import { useGroupWebSocket } from '../hooks/useGroupWebSocket';
import Navbar from '../components/Navbar';

export default function GroupDetailPage() {
  const { groupId } = useParams<{ groupId: string }>()
  const [activeTab, setActiveTab] = useState<'expenses'|'balances'|'debts'|'settle'|'activity'|'total'>('expenses')
  
  // Group details
  const { data: group } = useQuery({
    queryKey: ['groups', groupId],
    queryFn: () => groupsApi.get(groupId!),
    enabled: !!groupId
  });

  const queryClient = useQueryClient();
  const [showAddMember, setShowAddMember] = useState(false);
  const [memberEmail, setMemberEmail] = useState('');
  const [addMemberError, setAddMemberError] = useState('');

  const addMemberMutation = useMutation({
    mutationFn: (email: string) => groupsApi.addMember(groupId!, email),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groups', groupId] });
      setShowAddMember(false);
      setMemberEmail('');
      setAddMemberError('');
    },
    onError: (err: any) => {
      const msg = err.response?.data?.message || err.message || 'Failed to add member';
      setAddMemberError(msg);
    }
  });

  const handleAddMember = (e: React.FormEvent) => {
    e.preventDefault();
    setAddMemberError('');
    addMemberMutation.mutate(memberEmail);
  };

  return (
    <div className="min-h-screen">
      <Navbar />
      <main className="max-w-5xl mx-auto px-6 py-8">
        {/* Header */}
        <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-8 gap-4">
          <div>
            <h1 className="text-3xl font-bold flex items-center gap-3">
              {group?.name || 'Loading...'}
              {(group?.defaultCurrency || group?.currency) && (
                <span className="text-sm px-2 py-1 rounded-md" style={{ background: 'var(--color-surface-2)', color: 'var(--color-accent)' }}>
                  {group?.defaultCurrency || group?.currency}
                </span>
              )}
            </h1>
            <p className="mt-1 text-sm" style={{ color: 'var(--color-muted)' }}>
              {group?.members?.length || 0} members
            </p>
          </div>
          <div className="flex items-center gap-4">
            <div className="flex -space-x-2">
              {group?.members?.slice(0, 5).map((m: any, i: number) => (
                <div key={i} className="w-10 h-10 rounded-full border-2 flex items-center justify-center text-xs font-bold"
                  style={{ borderColor: 'var(--color-bg)', background: 'var(--color-surface-2)' }} title={m.name || m.email}>
                  {m.name ? m.name.substring(0, 2).toUpperCase() : 'U'}
                </div>
              ))}
            </div>
            <button
              onClick={() => setShowAddMember(true)}
              className="px-4 py-2 rounded-xl text-sm font-semibold transition-all duration-200"
              style={{ background: 'var(--color-surface-2)', color: 'var(--color-text)', border: '1px solid var(--color-border)' }}
            >
              + Add Member
            </button>
          </div>
        </div>

        {/* Tabs */}
        <div className="flex border-b mb-6 overflow-x-auto" style={{ borderColor: 'var(--color-border)' }}>
          {[
            { id: 'expenses', label: 'Expenses' },
            { id: 'balances', label: 'Balances' },
            { id: 'debts', label: 'Debts' },
            { id: 'settle', label: 'Settle Up' },
            { id: 'activity', label: 'Activity Log' },
            { id: 'total', label: 'Total' }
          ].map(t => (
            <button
              key={t.id}
              onClick={() => setActiveTab(t.id as any)}
              className={`px-6 py-3 font-medium text-sm whitespace-nowrap border-b-2 transition-colors ${activeTab === t.id ? '' : 'border-transparent'}`}
              style={{
                color: activeTab === t.id ? 'var(--color-accent)' : 'var(--color-muted)',
                borderColor: activeTab === t.id ? 'var(--color-accent)' : 'transparent'
              }}
            >
              {t.label}
            </button>
          ))}
        </div>

        {/* Tab Content */}
        <div className="pb-20">
          {activeTab === 'expenses' && <ExpensesTab groupId={groupId!} currency={group?.defaultCurrency || group?.currency || 'INR'} members={group?.members || []} />}
          {activeTab === 'balances' && <BalancesTab groupId={groupId!} currency={group?.defaultCurrency || group?.currency || 'INR'} />}
          {activeTab === 'debts' && <DebtsTab groupId={groupId!} currency={group?.defaultCurrency || group?.currency || 'INR'} onSettle={(_payeeId, _amt) => { setActiveTab('settle') }} />}
          {activeTab === 'settle' && <SettleUpTab groupId={groupId!} members={group?.members || []} currency={group?.defaultCurrency || group?.currency || 'INR'} />}
          {activeTab === 'activity' && <ActivityTab groupId={groupId!} members={group?.members || []} currency={group?.defaultCurrency || group?.currency || 'INR'} />}
          {activeTab === 'total' && <TotalTab groupId={groupId!} group={group} currency={group?.defaultCurrency || group?.currency || 'INR'} members={group?.members || []} />}
        </div>
      </main>

      {showAddMember && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div
            className="w-full max-w-md p-6 rounded-2xl"
            style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}
          >
            <h2 className="text-xl font-bold mb-2">Add Member</h2>
            <p className="text-sm mb-4" style={{ color: 'var(--color-muted)' }}>
              Enter the email address of a registered SettleUp user to invite them to this group.
            </p>
            <form onSubmit={handleAddMember} className="flex flex-col gap-4">
              <div>
                <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>User Email</label>
                <input
                  required
                  type="email"
                  placeholder="friend@example.com"
                  className="w-full px-4 py-2.5 rounded-xl outline-none"
                  style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
                  value={memberEmail}
                  onChange={(e) => setMemberEmail(e.target.value)}
                />
              </div>
              {addMemberError && (
                <div className="text-sm px-3 py-2 rounded-lg" style={{ color: 'var(--color-danger)', background: 'rgba(239, 68, 68, 0.1)' }}>
                  {addMemberError}
                </div>
              )}
              <div className="flex justify-end gap-3 mt-4">
                <button
                  type="button"
                  onClick={() => { setShowAddMember(false); setAddMemberError(''); setMemberEmail(''); }}
                  className="px-4 py-2 rounded-xl text-sm font-medium"
                  style={{ background: 'var(--color-surface-2)', color: 'var(--color-text)' }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={addMemberMutation.isPending}
                  className="px-4 py-2 rounded-xl text-sm font-semibold disabled:opacity-50"
                  style={{ background: 'var(--color-accent)', color: '#fff' }}
                >
                  {addMemberMutation.isPending ? 'Adding...' : 'Add Member'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function ExpensesTab({ groupId, currency, members }: { groupId: string, currency: string, members?: any[] }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);

  const { data } = useQuery({
    queryKey: ['expenses', groupId, page],
    queryFn: () => expensesApi.list(groupId, page, 20),
  });

  const reverseMutation = useMutation({
    mutationFn: (txId: string) => expensesApi.reverse(txId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['expenses', groupId] });
      queryClient.invalidateQueries({ queryKey: ['balances', groupId] });
    }
  });

  const [editingExp, setEditingExp] = useState<any | null>(null);
  const [editDesc, setEditDesc] = useState('');
  const [editAmount, setEditAmount] = useState('');
  const [editDate, setEditDate] = useState('');
  const [editPaidBy, setEditPaidBy] = useState('');
  const [editSplitType, setEditSplitType] = useState('EQUAL');
  const [editSplits, setEditSplits] = useState<Record<string, string>>({});
  const [editError, setEditError] = useState('');

  const editMutation = useMutation({
    mutationFn: ({ txId, data }: { txId: string; data: any }) => expensesApi.edit(txId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['expenses', groupId] });
      queryClient.invalidateQueries({ queryKey: ['balances', groupId] });
      setEditingExp(null);
    },
    onError: (err: any) => {
      setEditError(err.response?.data?.message || err.message || 'Failed to update expense');
    }
  });

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-xl font-bold">Group Expenses</h2>
        <button 
          onClick={() => navigate(`/groups/${groupId}/add-expense`)}
          className="px-4 py-2 rounded-xl text-sm font-semibold transition-all"
          style={{ background: 'var(--color-accent)', color: '#fff' }}
        >
          Add Expense
        </button>
      </div>

      <div className="space-y-4">
        {(() => {
          const reversedIndices = new Set<number>();
          const items = data?.content || [];
          for (let i = 0; i < items.length; i++) {
            const r = items[i];
            if (r.reversal || r.description?.startsWith('REVERSAL:')) {
              const origDesc = r.description?.replace(/^REVERSAL:\s*(REVERSAL:\s*)*/g, '');
              for (let j = i + 1; j < items.length; j++) {
                const candidate = items[j];
                if (
                  !reversedIndices.has(j) &&
                  !candidate.reversal &&
                  !candidate.description?.startsWith('REVERSAL:') &&
                  (candidate.description === origDesc || candidate.description?.includes(origDesc)) &&
                  candidate.totalAmount === r.totalAmount
                ) {
                  reversedIndices.add(j);
                  break;
                }
              }
            }
          }

          return items
            .filter((exp: any, idx: number) => {
              if (exp.reversal || exp.description?.startsWith('REVERSAL:')) return false;
              if (reversedIndices.has(idx)) return false;
              return true;
            })
            .map((exp: any) => (
          <div key={exp.transactionId || exp.id} className="p-4 rounded-xl flex justify-between items-center" style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}>
            <div>
              <div className="font-semibold">{exp.description}</div>
              <div className="text-xs mt-1 flex gap-2 items-center" style={{ color: 'var(--color-muted)' }}>
                <span>Paid by {members?.find((m: any) => (m.id || m.userId) === exp.paidByUserId)?.name || exp.paidBy?.name || exp.paidByUserId || 'Unknown'}</span>
                <span>•</span>
                <span>{new Date(exp.createdAt).toLocaleDateString()}</span>
                <span className="px-2 py-0.5 rounded text-[10px] font-bold" style={{ background: 'var(--color-surface-2)' }}>{exp.splitType}</span>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <div className="font-bold text-lg mr-2">{currency} {exp.totalAmount}</div>
              <button 
                onClick={() => {
                  setEditingExp(exp);
                  setEditDesc(exp.description || '');
                  setEditAmount(exp.totalAmount || '');
                  setEditDate(exp.createdAt ? exp.createdAt.split('T')[0] : new Date().toISOString().split('T')[0]);
                  setEditPaidBy(exp.paidByUserId || '');
                  setEditSplitType(exp.splitType || 'EQUAL');
                  setEditSplits({});
                  setEditError('');
                }}
                className="text-xs px-3 py-1.5 rounded-lg font-medium transition-colors hover:opacity-80"
                style={{ color: 'var(--color-accent)', background: 'rgba(99, 102, 241, 0.1)' }}
              >
                Edit
              </button>
              <button 
                onClick={() => reverseMutation.mutate(exp.transactionId || exp.id)}
                disabled={reverseMutation.isPending}
                className="text-xs px-3 py-1.5 rounded-lg font-medium transition-colors hover:opacity-80 disabled:opacity-50"
                style={{ color: 'var(--color-danger)', background: 'rgba(239, 68, 68, 0.1)' }}
              >
                Reverse
              </button>
            </div>
          </div>
        ));
        })()}
        {data?.content?.length === 0 && (
          <div className="text-center py-10" style={{ color: 'var(--color-muted)' }}>No expenses yet.</div>
        )}
      </div>

      {data?.totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-8">
          <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="px-3 py-1 rounded" style={{ background: 'var(--color-surface-2)' }}>Prev</button>
          <span className="px-3 py-1">Page {page + 1} of {data.totalPages}</span>
          <button disabled={page === data.totalPages - 1} onClick={() => setPage(p => p + 1)} className="px-3 py-1 rounded" style={{ background: 'var(--color-surface-2)' }}>Next</button>
        </div>
      )}

      {editingExp && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div
            className="w-full max-w-md p-6 rounded-2xl max-h-[90vh] overflow-y-auto"
            style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}
          >
            <h3 className="text-xl font-bold mb-4">Edit Transaction</h3>
            {editError && (
              <div className="p-3 mb-4 rounded-xl text-sm" style={{ background: 'rgba(239, 68, 68, 0.1)', color: 'var(--color-danger)' }}>
                {editError}
              </div>
            )}
            <form
              onSubmit={(e) => {
                e.preventDefault();
                let splitEntries = undefined;
                let actualSplitType = editSplitType;

                if (editSplitType === 'PERCENTAGE') {
                  const sum = Object.values(editSplits).reduce((acc, v) => acc + (parseFloat(v) || 0), 0);
                  if (Math.abs(sum - 100) > 0.1) {
                    alert('Percentages must sum to 100');
                    return;
                  }
                  splitEntries = members?.map((m: any) => ({
                    userId: m.id || m.userId,
                    value: parseFloat(editSplits[m.id || m.userId] || '0')
                  }));
                } else if (editSplitType === 'CUSTOM') {
                  const sum = Object.values(editSplits).reduce((acc, v) => acc + (parseFloat(v) || 0), 0);
                  if (Math.abs(sum - parseFloat(editAmount)) > 0.01) {
                    alert('Custom amounts must sum to total amount');
                    return;
                  }
                  splitEntries = members?.map((m: any) => ({
                    userId: m.id || m.userId,
                    value: parseFloat(editSplits[m.id || m.userId] || '0')
                  }));
                }

                editMutation.mutate({
                  txId: editingExp.transactionId || editingExp.id,
                  data: {
                    description: editDesc,
                    totalAmount: parseFloat(editAmount),
                    paidBy: editPaidBy,
                    splitType: actualSplitType,
                    splits: splitEntries,
                    expenseDate: editDate
                  }
                });
              }}
              className="flex flex-col gap-4"
            >
              <div>
                <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Description</label>
                <input
                  required
                  type="text"
                  value={editDesc}
                  onChange={(e) => setEditDesc(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-xl outline-none"
                  style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Amount</label>
                  <input
                    required
                    type="number"
                    step="0.01"
                    min="0"
                    value={editAmount}
                    onChange={(e) => setEditAmount(e.target.value)}
                    className="w-full px-4 py-2.5 rounded-xl outline-none"
                    style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Date</label>
                  <input
                    required
                    type="date"
                    value={editDate}
                    onChange={(e) => setEditDate(e.target.value)}
                    className="w-full px-4 py-2.5 rounded-xl outline-none"
                    style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
                  />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Paid By</label>
                <select
                  required
                  value={editPaidBy}
                  onChange={(e) => setEditPaidBy(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-xl outline-none"
                  style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
                >
                  {members?.map((m: any) => (
                    <option key={m.id || m.userId} value={m.id || m.userId}>
                      {m.name || m.userId}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Split Type</label>
                <div className="flex gap-2 p-1 rounded-xl" style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)' }}>
                  {['EQUAL', 'PERCENTAGE', 'CUSTOM'].map((type) => (
                    <button
                      key={type}
                      type="button"
                      onClick={() => {
                        setEditSplitType(type);
                        setEditSplits({});
                      }}
                      className="flex-1 py-1.5 text-xs font-semibold rounded-lg transition-colors"
                      style={{
                        background: editSplitType === type ? 'var(--color-surface-2)' : 'transparent',
                        color: editSplitType === type ? 'var(--color-text)' : 'var(--color-muted)'
                      }}
                    >
                      {type}
                    </button>
                  ))}
                </div>
              </div>

              {editSplitType !== 'EQUAL' && (
                <div className="p-3 rounded-xl space-y-2" style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)' }}>
                  <div className="flex justify-between text-xs font-bold mb-1" style={{ color: 'var(--color-muted)' }}>
                    <span>MEMBER</span>
                    <span>{editSplitType === 'PERCENTAGE' ? '%' : currency}</span>
                  </div>
                  {members?.map((m: any) => {
                    const uid = m.id || m.userId;
                    return (
                      <div key={uid} className="flex justify-between items-center">
                        <span className="font-medium text-xs">{m.name || uid}</span>
                        <input
                          type="number"
                          step="0.01"
                          min="0"
                          value={editSplits[uid] || ''}
                          onChange={(e) =>
                            setEditSplits((prev) => ({ ...prev, [uid]: e.target.value }))
                          }
                          className="w-20 px-2 py-1 rounded-lg outline-none text-right text-xs"
                          placeholder="0"
                          style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
                        />
                      </div>
                    );
                  })}
                </div>
              )}

              <div className="flex justify-end gap-3 mt-2">
                <button
                  type="button"
                  onClick={() => setEditingExp(null)}
                  className="px-4 py-2 rounded-xl text-sm font-medium"
                  style={{ background: 'var(--color-surface-2)', color: 'var(--color-text)' }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={editMutation.isPending}
                  className="px-4 py-2 rounded-xl text-sm font-bold transition-opacity disabled:opacity-50"
                  style={{ background: 'var(--color-accent)', color: '#fff' }}
                >
                  {editMutation.isPending ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function BalancesTab({ groupId, currency }: { groupId: string, currency: string }) {
  const queryClient = useQueryClient();
  const { connected } = useGroupWebSocket(groupId, () => {
    queryClient.invalidateQueries({ queryKey: ['balances', groupId] })
    queryClient.invalidateQueries({ queryKey: ['debts', groupId] })
  })

  const { data: balanceData, isLoading } = useQuery({
    queryKey: ['balances', groupId],
    queryFn: () => groupsApi.getBalances(groupId),
  });

  const balances = Array.isArray(balanceData) ? balanceData : (balanceData?.balances || []);

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-xl font-bold flex items-center gap-2">
          Balances
          {connected && <span className="text-[10px] px-2 py-0.5 rounded-full font-bold flex items-center gap-1" style={{ color: 'var(--color-danger)', background: 'rgba(239, 68, 68, 0.1)' }}><span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse"></span>LIVE</span>}
        </h2>
      </div>

      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)', background: 'var(--color-surface)' }}>
        <table className="w-full text-left border-collapse">
          <thead>
            <tr style={{ background: 'var(--color-surface-2)', borderBottom: '1px solid var(--color-border)' }}>
              <th className="p-4 font-medium text-sm" style={{ color: 'var(--color-muted)' }}>Member</th>
              <th className="p-4 font-medium text-sm text-right" style={{ color: 'var(--color-muted)' }}>Net Balance</th>
            </tr>
          </thead>
          <tbody>
            {balances.map((b: any, i: number) => {
              const val = parseFloat(b.netBalance || b.balance || '0');
              return (
                <tr key={i} style={{ borderBottom: '1px solid var(--color-border)' }}>
                  <td className="p-4 font-medium">{b.name || b.user?.name || b.userId}</td>
                  <td className="p-4 text-right font-bold" style={{ color: val > 0 ? 'var(--color-success)' : val < 0 ? 'var(--color-danger)' : 'var(--color-muted)' }}>
                    {val > 0 ? '+' : ''}{val.toFixed(2)} {currency}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {(!balances || balances.length === 0) && !isLoading && (
          <div className="text-center py-8" style={{ color: 'var(--color-muted)' }}>No balances found.</div>
        )}
      </div>
    </div>
  );
}

function DebtsTab({ groupId, currency, onSettle: _onSettle }: { groupId: string, currency: string, onSettle: (p: string, a: string) => void }) {
  const { data: debtData, isLoading } = useQuery({
    queryKey: ['debts', groupId],
    queryFn: () => groupsApi.getSimplifiedDebts(groupId),
  });

  const debts = Array.isArray(debtData) ? debtData : (debtData?.settlementsSuggested || debtData?.debts || []);

  return (
    <div>
      <h2 className="text-xl font-bold mb-6">Simplified Debts</h2>
      <div className="space-y-3">
        {debts.map((d: any, i: number) => (
          <div key={i} className="p-4 rounded-xl flex items-center justify-between" style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}>
            <div className="flex items-center gap-3">
              <span className="font-semibold">{d.from || d.fromUser?.name || d.fromUserId}</span>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ color: 'var(--color-muted)' }}><path d="M5 12h14M12 5l7 7-7 7"/></svg>
              <span className="font-semibold">{d.to || d.toUser?.name || d.toUserId}</span>
              <span className="ml-2 font-bold" style={{ color: 'var(--color-accent)' }}>{currency} {d.amount}</span>
            </div>
          </div>
        ))}
        {(!debts || debts.length === 0) && !isLoading && (
          <div className="text-center py-8" style={{ color: 'var(--color-muted)' }}>All settled up!</div>
        )}
      </div>
    </div>
  );
}

function SettleUpTab({ groupId, members, currency }: { groupId: string, members: any[], currency: string }) {
  const [payeeId, setPayeeId] = useState('');
  const [amount, setAmount] = useState('');
  const [status, setStatus] = useState<any>(null);
  const [settlementId, setSettlementId] = useState<string | null>(null);
  const intervalRef = useRef<any>(null);

  const initiateMutation = useMutation({
    mutationFn: (data: { pId: string, amt: string, idemp: string }) => settlementsApi.initiate(groupId, data.pId, data.amt, data.idemp),
    onSuccess: (data) => {
      setSettlementId(data.settlementId)
      setStatus(data)
    }
  });

  useEffect(() => {
    if (settlementId && status?.status !== 'COMPLETED' && status?.status !== 'FAILED') {
      intervalRef.current = setInterval(() => {
        settlementsApi.get(settlementId).then(res => {
          setStatus(res);
          if (res.status === 'COMPLETED' || res.status === 'FAILED') {
            clearInterval(intervalRef.current);
          }
        });
      }, 2000);
    }
    return () => clearInterval(intervalRef.current);
  }, [settlementId, status]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    initiateMutation.mutate({ pId: payeeId, amt: amount, idemp: crypto.randomUUID() });
  };

  const getStatusColor = (s: string) => {
    if (s === 'PENDING') return 'var(--color-warning)';
    if (s === 'PROCESSING') return 'var(--color-accent)';
    if (s === 'COMPLETED') return 'var(--color-success)';
    if (s === 'FAILED') return 'var(--color-danger)';
    return 'var(--color-muted)';
  };

  return (
    <div className="max-w-md">
      <h2 className="text-xl font-bold mb-6">Record a Payment</h2>
      
      {status ? (
        <div className="p-6 rounded-2xl text-center" style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}>
          <div className="inline-block px-3 py-1 rounded-full text-xs font-bold mb-4" style={{ background: `${getStatusColor(status.status)}20`, color: getStatusColor(status.status) }}>
            {status.status}
          </div>
          <div className="text-2xl font-bold mb-2">{currency} {status.amount}</div>
          {status.mockUpiRef && (
            <div className="text-sm mt-4 p-3 rounded-lg" style={{ background: 'var(--color-surface-2)', color: 'var(--color-muted)' }}>
              UPI Ref: <span className="font-mono text-white">{status.mockUpiRef}</span>
            </div>
          )}
          {(status.status === 'COMPLETED' || status.status === 'FAILED') && (
            <button onClick={() => { setStatus(null); setSettlementId(null); setAmount(''); }} className="mt-6 text-sm font-medium underline" style={{ color: 'var(--color-accent)' }}>
              Record another
            </button>
          )}
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Pay to</label>
            <select required value={payeeId} onChange={e => setPayeeId(e.target.value)}
              className="w-full px-4 py-3 rounded-xl outline-none appearance-none"
              style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}>
              <option value="">Select member...</option>
              {members.map(m => (
                <option key={m.id || m.userId} value={m.id || m.userId}>{m.name || m.userId}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Amount</label>
            <div className="relative">
              <span className="absolute left-4 top-1/2 -translate-y-1/2 font-medium" style={{ color: 'var(--color-muted)' }}>{currency}</span>
              <input required type="number" step="0.01" value={amount} onChange={e => setAmount(e.target.value)}
                className="w-full pl-12 pr-4 py-3 rounded-xl outline-none"
                style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }} placeholder="0.00" />
            </div>
          </div>
          <button type="submit" disabled={initiateMutation.isPending}
            className="w-full py-3 rounded-xl font-bold mt-4 transition-opacity disabled:opacity-50"
            style={{ background: 'var(--color-accent)', color: '#fff' }}>
            {initiateMutation.isPending ? 'Processing...' : 'Settle Up'}
          </button>
        </form>
      )}
    </div>
  );
}

function ActivityTab({ groupId, currency, members }: { groupId: string, currency: string, members?: any[] }) {
  const [page, setPage] = useState(0);
  const { data } = useQuery({
    queryKey: ['expenses', groupId, page],
    queryFn: () => expensesApi.list(groupId, page, 50),
  });

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <div>
          <h2 className="text-xl font-bold">Group Activity Log</h2>
          <p className="text-sm mt-0.5" style={{ color: 'var(--color-muted)' }}>
            Complete audit trail of all expenses, settlements, and reversals
          </p>
        </div>
      </div>

      <div className="space-y-3">
        {data?.content?.map((exp: any) => {
          const isReversal = exp.reversal || exp.description?.startsWith('REVERSAL:');
          const isSettlement = exp.description?.startsWith('SETTLEMENT:');
          const payerName = members?.find((m: any) => (m.id || m.userId) === exp.paidByUserId)?.name || exp.paidBy?.name || exp.paidByUserId || 'Unknown';
          const dateStr = new Date(exp.createdAt).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });

          let icon = '💸';
          let title = `Expense added: "${exp.description}"`;
          let badgeColor = 'var(--color-surface-2)';
          let textColor = 'var(--color-text)';

          if (isReversal) {
            icon = '↩️';
            title = `Reversed: ${exp.description.replace(/^REVERSAL:\s*(REVERSAL:\s*)*/g, '')}`;
            badgeColor = 'rgba(239, 68, 68, 0.15)';
            textColor = 'var(--color-danger)';
          } else if (isSettlement) {
            icon = '🤝';
            title = exp.description;
            badgeColor = 'rgba(34, 197, 94, 0.15)';
            textColor = 'var(--color-success)';
          }

          return (
            <div 
              key={exp.transactionId || exp.id} 
              className="p-4 rounded-xl flex items-center justify-between gap-4 transition-all"
              style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}
            >
              <div className="flex items-center gap-4 min-w-0">
                <div 
                  className="w-10 h-10 rounded-full flex items-center justify-center shrink-0 text-lg shadow-sm"
                  style={{ background: badgeColor }}
                >
                  {icon}
                </div>
                <div className="min-w-0">
                  <div className="font-semibold text-sm truncate" style={{ color: isReversal ? 'var(--color-danger)' : 'var(--color-text)' }}>
                    {title}
                  </div>
                  <div className="text-xs mt-1 flex gap-2 items-center flex-wrap" style={{ color: 'var(--color-muted)' }}>
                    <span>By {payerName}</span>
                    <span>•</span>
                    <span>{dateStr}</span>
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-bold uppercase" style={{ background: 'var(--color-surface-2)' }}>
                      {isReversal ? 'REVERSAL' : isSettlement ? 'SETTLEMENT' : exp.splitType}
                    </span>
                  </div>
                </div>
              </div>

              <div className="text-right shrink-0">
                <div className="font-bold text-sm" style={{ color: textColor }}>
                  {isReversal ? '-' : ''}{currency} {exp.totalAmount}
                </div>
              </div>
            </div>
          );
        })}

        {data?.content?.length === 0 && (
          <div className="text-center py-10" style={{ color: 'var(--color-muted)' }}>No activity recorded yet.</div>
        )}
      </div>

      {data?.totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-8">
          <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="px-3 py-1 rounded text-sm font-medium" style={{ background: 'var(--color-surface-2)' }}>Prev</button>
          <span className="px-3 py-1 text-sm font-medium" style={{ color: 'var(--color-muted)' }}>Page {page + 1} of {data.totalPages}</span>
          <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)} className="px-3 py-1 rounded text-sm font-medium" style={{ background: 'var(--color-surface-2)' }}>Next</button>
        </div>
      )}
    </div>
  );
}

function formatMonthYear(ym: string) {
  const [y, m] = ym.split('-').map(Number);
  const date = new Date(y, m - 1, 1);
  return date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
}

function formatMonthShort(ym: string) {
  const [y, m] = ym.split('-').map(Number);
  const date = new Date(y, m - 1, 1);
  return date.toLocaleDateString('en-US', { month: 'short' }).toUpperCase();
}

function getThreeMonths(currentYm: string): string[] {
  const [y, m] = currentYm.split('-').map(Number);
  const res: string[] = [];
  for (let offset = -2; offset <= 0; offset++) {
    const d = new Date(y, m - 1 + offset, 1);
    const str = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    res.push(str);
  }
  return res;
}

function TotalTab({ groupId, group, currency, members }: { groupId: string; group: any; currency: string; members: any[] }) {
  const [selectedMonth, setSelectedMonth] = useState<string>(() => {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  });
  const [isAllTime, setIsAllTime] = useState<boolean>(false);

  const { data } = useQuery({
    queryKey: ['expenses', groupId, 'all-for-total'],
    queryFn: () => expensesApi.list(groupId, 0, 500),
  });

  const { data: me } = useQuery({
    queryKey: ['users', 'me'],
    queryFn: () => usersApi.getMe(),
  });

  const myUserId = members.find((m: any) => 
    (m.id || m.userId) === (me?.id || me?.userId) || 
    m.email === me?.email || 
    m.email === localStorage.getItem('userEmail')
  )?.id || members.find((m: any) => 
    (m.id || m.userId) === (me?.id || me?.userId) || 
    m.email === me?.email || 
    m.email === localStorage.getItem('userEmail')
  )?.userId || me?.id || me?.userId;

  const items = data?.content || [];
  const reversedIndices = new Set<number>();
  for (let i = 0; i < items.length; i++) {
    const r = items[i];
    if (r.reversal || r.description?.startsWith('REVERSAL:')) {
      const origDesc = r.description?.replace(/^REVERSAL:\s*(REVERSAL:\s*)*/g, '');
      for (let j = i + 1; j < items.length; j++) {
        const candidate = items[j];
        if (
          !reversedIndices.has(j) &&
          !candidate.reversal &&
          !candidate.description?.startsWith('REVERSAL:') &&
          (candidate.description === origDesc || candidate.description?.includes(origDesc)) &&
          candidate.totalAmount === r.totalAmount
        ) {
          reversedIndices.add(j);
          break;
        }
      }
    }
  }

  const validExpenses = items.filter((exp: any, idx: number) => {
    if (exp.reversal || exp.description?.startsWith('REVERSAL:')) return false;
    if (reversedIndices.has(idx)) return false;
    if (exp.description?.startsWith('SETTLEMENT:')) return false;
    return true;
  });

  const getUserShare = (exp: any): number => {
    if (exp.ledgerEntries && Array.isArray(exp.ledgerEntries)) {
      const myDebit = exp.ledgerEntries.find((e: any) => e.userId === myUserId && e.entryType === 'DEBIT');
      if (myDebit) {
        return parseFloat(myDebit.amount || '0');
      }
    }
    const total = parseFloat(exp.totalAmount || '0');
    if (exp.splitType === 'EQUAL') {
      return members.length > 0 ? total / members.length : 0;
    }
    return 0;
  };

  const getMonthlyStats = (ym: string) => {
    const monthExps = validExpenses.filter((e: any) => {
      const dateStr = e.createdAt ? e.createdAt.substring(0, 7) : '';
      return dateStr === ym;
    });
    const total = monthExps.reduce((acc: number, e: any) => acc + parseFloat(e.totalAmount || '0'), 0);
    const share = monthExps.reduce((acc: number, e: any) => acc + getUserShare(e), 0);
    return { total, share };
  };

  const activeExpenses = isAllTime
    ? validExpenses
    : validExpenses.filter((e: any) => (e.createdAt ? e.createdAt.substring(0, 7) : '') === selectedMonth);

  const totalSpent = activeExpenses.reduce((acc: number, e: any) => acc + parseFloat(e.totalAmount || '0'), 0);
  const userShare = activeExpenses.reduce((acc: number, e: any) => acc + getUserShare(e), 0);
  const sharePct = totalSpent > 0 ? Math.round((userShare / totalSpent) * 100) : 0;

  const threeMonths = getThreeMonths(selectedMonth);
  const monthlyData = threeMonths.map((ym) => {
    const stats = getMonthlyStats(ym);
    return {
      ym,
      label: formatMonthShort(ym),
      total: stats.total,
      share: stats.share,
      isSelected: ym === selectedMonth,
    };
  });

  const maxTotal = Math.max(100, ...monthlyData.map((d) => d.total));

  return (
    <div className="max-w-md mx-auto py-4">
      {/* Title */}
      <div className="mb-6">
        <h2 className="text-3xl font-extrabold tracking-tight">{group?.name || 'Group'}</h2>
        <p className="text-base font-semibold mt-1" style={{ color: 'var(--color-muted)' }}>
          {isAllTime ? 'All time group spending' : `${formatMonthYear(selectedMonth)} group spending`}
        </p>
      </div>

      {/* 3-Month Bar Chart Box */}
      <div className="relative py-8 my-6 border-t border-b border-dashed" style={{ borderColor: 'var(--color-border)' }}>
        <div className="flex justify-center items-end gap-12 h-44 px-4">
          {monthlyData.map((d) => {
            const barHeightPx = Math.max(16, Math.round((d.total / maxTotal) * 150));
            const sharePctOfBar = d.total > 0 ? Math.round((d.share / d.total) * 100) : 0;
            return (
              <div
                key={d.ym}
                onClick={() => {
                  setSelectedMonth(d.ym);
                  setIsAllTime(false);
                }}
                className="flex flex-col items-center cursor-pointer group"
              >
                <div className="w-12 h-40 flex items-end justify-center">
                  <div
                    style={{ height: `${barHeightPx}px` }}
                    className="w-11 rounded-full overflow-hidden flex flex-col transition-all duration-300 group-hover:scale-105"
                  >
                    {/* Top segment (Other spending) */}
                    <div
                      style={{
                        height: `${100 - sharePctOfBar}%`,
                        backgroundColor: d.isSelected ? '#38bdf8' : '#9ca3af'
                      }}
                      className="w-full transition-colors"
                    />
                    {/* Bottom segment (Your share) */}
                    <div
                      style={{
                        height: `${sharePctOfBar}%`,
                        backgroundColor: d.isSelected ? '#0284c7' : '#4b5563'
                      }}
                      className="w-full transition-colors"
                    />
                  </div>
                </div>
                {/* Month label */}
                <div
                  className={`mt-3 text-xs tracking-wider transition-colors ${
                    d.isSelected ? 'text-white font-extrabold' : 'text-gray-400 font-bold'
                  }`}
                >
                  {d.label}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Total spent & Your share Metrics */}
      <div className="mt-8 space-y-6">
        <div>
          <div className="text-sm font-semibold flex items-center gap-1.5" style={{ color: 'var(--color-muted)' }}>
            <span>Total spent</span>
            <span className="w-4 h-4 rounded-full border flex items-center justify-center text-[11px]" style={{ borderColor: 'var(--color-border)' }}>?</span>
          </div>
          <div className="text-4xl font-extrabold mt-1 tracking-tight" style={{ color: '#38bdf8' }}>
            {currency} {totalSpent.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </div>
        </div>

        <div>
          <div className="text-sm font-semibold flex items-center gap-1.5" style={{ color: 'var(--color-muted)' }}>
            <span>Your share</span>
            <span className="w-4 h-4 rounded-full border flex items-center justify-center text-[11px]" style={{ borderColor: 'var(--color-border)' }}>?</span>
          </div>
          <div className="text-4xl font-extrabold mt-1 tracking-tight" style={{ color: '#0284c7' }}>
            {currency} {userShare.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </div>
          <div className="text-sm font-medium mt-1.5" style={{ color: 'var(--color-muted)' }}>
            {sharePct}% of total group spending
          </div>
        </div>
      </div>

      {/* Bottom month switcher pill */}
      <div
        className="mt-10 flex items-center justify-between p-2 rounded-2xl"
        style={{ background: 'var(--color-surface-2)', border: '1px solid var(--color-border)' }}
      >
        <button
          onClick={() => setIsAllTime(!isAllTime)}
          className={`px-5 py-2.5 rounded-xl text-sm font-bold transition-all ${
            isAllTime ? 'bg-white/20 text-white' : 'text-gray-400 hover:text-white'
          }`}
        >
          All time
        </button>
        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              setIsAllTime(false);
              const [y, m] = selectedMonth.split('-').map(Number);
              const d = new Date(y, m - 2, 1);
              setSelectedMonth(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
            }}
            className="w-9 h-9 rounded-xl flex items-center justify-center text-lg font-bold hover:bg-white/10"
          >
            ‹
          </button>
          <span className="text-sm font-bold min-w-[110px] text-center">
            {formatMonthYear(selectedMonth)}
          </span>
          <button
            onClick={() => {
              setIsAllTime(false);
              const [y, m] = selectedMonth.split('-').map(Number);
              const d = new Date(y, m, 1);
              setSelectedMonth(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
            }}
            className="w-9 h-9 rounded-xl flex items-center justify-center text-lg font-bold hover:bg-white/10"
          >
            ›
          </button>
        </div>
      </div>
    </div>
  );
}


