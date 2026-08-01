import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { groupsApi, expensesApi } from '../api/endpoints';
import Navbar from '../components/Navbar';

export default function AddExpensePage() {
  const { groupId } = useParams<{ groupId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: group } = useQuery({
    queryKey: ['groups', groupId],
    queryFn: () => groupsApi.get(groupId!),
    enabled: !!groupId
  });

  const [description, setDescription] = useState('');
  const [totalAmount, setTotalAmount] = useState('');
  const [paidBy, setPaidBy] = useState('');
  const [splitType, setSplitType] = useState('EQUAL');
  const [expenseDate, setExpenseDate] = useState(() => new Date().toISOString().split('T')[0]);
  
  // Custom & Percentage split state
  const [splits, setSplits] = useState<Record<string, string>>({});
  const [lentTo, setLentTo] = useState('');

  useEffect(() => {
    if (group?.members && !paidBy) {
      setPaidBy(group.members[0]?.id || group.members[0]?.userId || '');
    }
    if (group?.members && !lentTo && group.members.length > 1) {
      const other = group.members.find((m: any) => (m.id || m.userId) !== paidBy);
      setLentTo(other?.id || other?.userId || group.members[0]?.id || group.members[0]?.userId || '');
    }
  }, [group, paidBy, lentTo]);

  const handleSplitChange = (userId: string, val: string) => {
    setSplits(prev => ({ ...prev, [userId]: val }));
  };

  const currentTotal = Object.values(splits).reduce((acc, v) => acc + (parseFloat(v) || 0), 0);

  const [errorMsg, setErrorMsg] = useState('');

  const createExpense = useMutation({
    mutationFn: (data: any) => expensesApi.create(groupId!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['expenses', groupId] });
      queryClient.invalidateQueries({ queryKey: ['balances', groupId] });
      navigate(`/groups/${groupId}`);
    },
    onError: (err: any) => {
      const msg = err.response?.data?.message || err.message || 'Failed to save expense';
      setErrorMsg(msg);
    }
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg('');

    let splitEntries = undefined;
    let actualSplitType = splitType;

    if (splitType === 'LENT') {
      if (!lentTo) {
        alert('Please select who you are lending to');
        return;
      }
      actualSplitType = 'CUSTOM';
      splitEntries = [{ userId: lentTo, value: parseFloat(totalAmount) }];
    } else if (splitType === 'PERCENTAGE') {
      if (Math.abs(currentTotal - 100) > 0.1) {
        alert('Percentages must sum to 100');
        return;
      }
      splitEntries = group.members.map((m: any) => {
        const uid = m.id || m.userId;
        const val = parseFloat(splits[uid] || '0');
        return { userId: uid, value: val };
      });
    } else if (splitType === 'CUSTOM') {
      if (Math.abs(currentTotal - parseFloat(totalAmount)) > 0.01) {
        alert('Custom amounts must sum to total amount');
        return;
      }
      splitEntries = group.members.map((m: any) => {
        const uid = m.id || m.userId;
        return { userId: uid, value: parseFloat(splits[uid] || '0') };
      });
    }

    createExpense.mutate({
      description,
      totalAmount: parseFloat(totalAmount),
      paidBy,
      splitType: actualSplitType,
      splits: splitEntries,
      expenseDate
    });
  };

  return (
    <div className="min-h-screen">
      <Navbar />
      <main className="max-w-2xl mx-auto px-6 py-8">
        <button 
          onClick={() => navigate(`/groups/${groupId}`)}
          className="flex items-center gap-2 text-sm font-medium mb-6 transition-colors"
          style={{ color: 'var(--color-muted)' }}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
          Back to Group
        </button>

        <div className="p-8 rounded-2xl" style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}>
          <h1 className="text-2xl font-bold mb-6">Add Expense</h1>
          
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Description</label>
              <input required type="text" value={description} onChange={e => setDescription(e.target.value)}
                className="w-full px-4 py-3 rounded-xl outline-none" placeholder="e.g. Dinner at Raj's"
                style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)' }} />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Amount</label>
                <div className="relative flex items-center">
                  <span 
                    className="absolute left-4 top-1/2 -translate-y-1/2 font-bold select-none pr-3" 
                    style={{ color: 'var(--color-muted)' }}
                  >
                    {group?.defaultCurrency || group?.currency || 'INR'}
                  </span>
                  <input required type="number" step="0.01" min="0" value={totalAmount} onChange={e => setTotalAmount(e.target.value)}
                    className="w-full pl-16 pr-4 py-3 rounded-xl outline-none" placeholder="0.00"
                    style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)' }} />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Paid By</label>
                <select required value={paidBy} onChange={e => setPaidBy(e.target.value)}
                  className="w-full px-4 py-3 rounded-xl outline-none appearance-none"
                  style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)' }}>
                  {group?.members?.map((m: any) => (
                    <option key={m.id || m.userId} value={m.id || m.userId}>{m.name || m.userId}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Date</label>
                <input required type="date" value={expenseDate} onChange={e => setExpenseDate(e.target.value)}
                  className="w-full px-4 py-3 rounded-xl outline-none"
                  style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--color-muted)' }}>Split Type</label>
              <div className="flex gap-2 p-1 rounded-xl" style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)' }}>
                {['EQUAL', 'PERCENTAGE', 'CUSTOM', 'LENT'].map(type => (
                  <button key={type} type="button" onClick={() => { setSplitType(type); setSplits({}); }}
                    className={`flex-1 py-2 text-sm font-semibold rounded-lg transition-colors`}
                    style={{ 
                      background: splitType === type ? 'var(--color-surface-2)' : 'transparent',
                      color: splitType === type ? 'var(--color-text)' : 'var(--color-muted)' 
                    }}>
                    {type}
                  </button>
                ))}
              </div>
            </div>

            {splitType !== 'EQUAL' && splitType !== 'LENT' && (
              <div className="p-4 rounded-xl space-y-3" style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)' }}>
                <div className="flex justify-between text-xs font-bold mb-2" style={{ color: 'var(--color-muted)' }}>
                  <span>MEMBER</span>
                  <span>{splitType === 'PERCENTAGE' ? '%' : (group?.defaultCurrency || group?.currency || 'INR')}</span>
                </div>
                {group?.members?.map((m: any) => {
                  const uid = m.id || m.userId;
                  return (
                    <div key={uid} className="flex justify-between items-center">
                      <span className="font-medium text-sm">{m.name || uid}</span>
                      <input type="number" step="0.01" min="0" value={splits[uid] || ''} onChange={e => handleSplitChange(uid, e.target.value)}
                        className="w-24 px-3 py-1.5 rounded-lg outline-none text-right text-sm" placeholder="0"
                        style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }} />
                    </div>
                  );
                })}
                <div className="pt-3 border-t mt-3 flex justify-between text-sm font-bold" style={{ borderColor: 'var(--color-border)' }}>
                  <span>Total</span>
                  <span style={{ color: Math.abs(currentTotal - (splitType === 'PERCENTAGE' ? 100 : parseFloat(totalAmount||'0'))) > 0.1 ? 'var(--color-danger)' : 'var(--color-success)' }}>
                    {currentTotal.toFixed(2)} {splitType === 'PERCENTAGE' ? '/ 100%' : `/ ${parseFloat(totalAmount||'0').toFixed(2)}`}
                  </span>
                </div>
              </div>
            )}

            {splitType === 'LENT' && (
              <div className="p-4 rounded-xl space-y-3" style={{ background: 'var(--color-bg)', border: '1px solid var(--color-border)' }}>
                <label className="block text-sm font-medium mb-1" style={{ color: 'var(--color-muted)' }}>
                  Lend To (Friend who owes 100% of {group?.defaultCurrency || group?.currency || 'INR'} {totalAmount || '0.00'})
                </label>
                <select required value={lentTo} onChange={e => setLentTo(e.target.value)}
                  className="w-full px-4 py-3 rounded-xl outline-none appearance-none"
                  style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}>
                  <option value="">Select friend...</option>
                  {group?.members?.map((m: any) => {
                    const uid = m.id || m.userId;
                    return (
                      <option key={uid} value={uid}>
                        {m.name || m.email || uid} {uid === paidBy ? '(You / Payer)' : ''}
                      </option>
                    );
                  })}
                </select>
                <p className="text-xs" style={{ color: 'var(--color-muted)' }}>
                  💡 This records a direct 1-to-1 loan: {group?.members?.find((m:any) => (m.id||m.userId) === paidBy)?.name || 'The payer'} paid the full amount for {group?.members?.find((m:any) => (m.id||m.userId) === lentTo)?.name || 'this person'}.
                </p>
              </div>
            )}

            {errorMsg && (
              <div className="p-3 rounded-xl text-sm font-medium" style={{ background: 'rgba(239, 68, 68, 0.1)', color: 'var(--color-danger)' }}>
                {errorMsg}
              </div>
            )}

            <button type="submit" disabled={createExpense.isPending}
              className="w-full py-3.5 rounded-xl font-bold mt-6 transition-opacity disabled:opacity-50"
              style={{ background: 'var(--color-accent)', color: '#fff' }}>
              {createExpense.isPending ? 'Saving...' : 'Save Expense'}
            </button>
          </form>
        </div>
      </main>
    </div>
  );
}
