import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { CategoryResponse, ExpenseTemplate, Role, UserResponse, UserStatus } from '../types';

interface AdminPanelProps {
  currentUser: UserResponse;
  categories: CategoryResponse[];
  templates: ExpenseTemplate[];
  lowThreshold: number;
  onRefresh: () => void;
  toast: (msg: string) => void;
  activeTab: 'categories' | 'templates' | 'users' | 'threshold';
  setActiveTab: (tab: 'categories' | 'templates' | 'users' | 'threshold') => void;
}

export const AdminPanel: React.FC<AdminPanelProps> = ({
  currentUser,
  categories,
  templates,
  lowThreshold,
  onRefresh,
  toast,
  activeTab,
  setActiveTab,
}) => {
  const [loading, setLoading] = useState<boolean>(false);

  // Categories form states
  const [newCatName, setNewCatName] = useState<string>('');
  const [newSubNames, setNewSubNames] = useState<Record<number, string>>({});
  const [editingCategoryId, setEditingCategoryId] = useState<number | null>(null);
  const [editingCategoryName, setEditingCategoryName] = useState<string>('');
  const [editingSubcategoryId, setEditingSubcategoryId] = useState<number | null>(null);
  const [editingSubcategoryName, setEditingSubcategoryName] = useState<string>('');

  // Templates form states
  const [tempName, setTempName] = useState<string>('');
  const [tempCategory, setTempCategory] = useState<string>('');
  const [tempDesc, setTempDesc] = useState<string>('');
  const [tempAmount, setTempAmount] = useState<string>('');
  const [tempReceiptRequired, setTempReceiptRequired] = useState<boolean>(true);
  const [editingTemplateId, setEditingTemplateId] = useState<number | null>(null);

  // Users directory states
  const [usersList, setUsersList] = useState<UserResponse[]>([]);
  const [newUserName, setNewUserName] = useState<string>('');
  const [newUserEmail, setNewUserEmail] = useState<string>('');
  const [newUserRole, setNewUserRole] = useState<Role>('USER');

  // Threshold state
  const [thresholdInput, setThresholdInput] = useState<string>(lowThreshold.toString());

  useEffect(() => {
    setThresholdInput(lowThreshold.toString());
  }, [lowThreshold]);

  useEffect(() => {
    if (activeTab === 'users' && currentUser.role === 'ADMIN') {
      fetchUsers();
    }
  }, [activeTab, currentUser]);

  const fetchUsers = async () => {
    try {
      const data = await api.users.list();
      setUsersList(data);
    } catch (err) {
      toast('❌ Error loading users: ' + (err instanceof Error ? err.message : String(err)));
    }
  };

  if (currentUser.role !== 'ADMIN') {
    return (
      <div className="w-full bg-slate-900 border border-slate-800 rounded-xl p-8 text-center text-slate-400">
        🔒 Administrative access is required to view configurations.
      </div>
    );
  }

  // --- Category Handlers ---
  const handleAddCategory = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCatName.trim()) return;
    setLoading(true);
    try {
      await api.categories.createCategory(newCatName.trim());
      setNewCatName('');
      toast('✅ Category added!');
      onRefresh();
    } catch (err) {
      toast('❌ Failed to add category: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  const handleAddSubcategory = async (categoryId: number) => {
    const subName = newSubNames[categoryId]?.trim();
    if (!subName) return;
    setLoading(true);
    try {
      await api.categories.createSubcategory(categoryId, subName);
      setNewSubNames((prev) => ({ ...prev, [categoryId]: '' }));
      toast('✅ Subcategory added!');
      onRefresh();
    } catch (err) {
      toast('❌ Failed to add subcategory: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteCategory = async (id: number, name: string) => {
    if (!confirm(`Delete category "${name}"? This will delete all its nested subcategories.`)) return;
    setLoading(true);
    try {
      await api.categories.deleteCategory(id);
      toast('🗑 Category deleted.');
      onRefresh();
    } catch (err) {
      toast('❌ Error deleting: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteSubcategory = async (id: number) => {
    if (!confirm('Delete this subcategory?')) return;
    setLoading(true);
    try {
      await api.categories.deleteSubcategory(id);
      toast('🗑 Subcategory deleted.');
      onRefresh();
    } catch (err) {
      toast('❌ Error deleting subcategory: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateCategory = async (id: number) => {
    if (!editingCategoryName.trim()) return;
    setLoading(true);
    try {
      await api.categories.updateCategory(id, editingCategoryName.trim());
      setEditingCategoryId(null);
      setEditingCategoryName('');
      toast('✏️ Category name updated!');
      onRefresh();
    } catch (err) {
      toast('❌ Failed to update category: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateSubcategory = async (id: number) => {
    if (!editingSubcategoryName.trim()) return;
    setLoading(true);
    try {
      await api.categories.updateSubcategory(id, editingSubcategoryName.trim());
      setEditingSubcategoryId(null);
      setEditingSubcategoryName('');
      toast('✏️ Subcategory name updated!');
      onRefresh();
    } catch (err) {
      toast('❌ Failed to update subcategory: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  // --- Template Handlers ---
  const handleSaveTemplate = async (e: React.FormEvent) => {
    e.preventDefault();
    const amountNum = parseFloat(tempAmount);
    if (!tempName.trim()) return toast('⚠️ Template name is required.');
    if (!tempCategory.trim()) return toast('⚠️ Category name is required.');
    if (isNaN(amountNum) || amountNum <= 0) return toast('⚠️ Amount must be greater than zero.');

    setLoading(true);
    try {
      const payload = {
        name: tempName.trim(),
        category: tempCategory,
        description: tempDesc.trim(),
        amount: amountNum,
        receiptRequired: tempReceiptRequired,
      };

      if (editingTemplateId) {
        await api.templates.update(editingTemplateId, payload);
        toast('✏️ Template updated!');
      } else {
        await api.templates.create(payload);
        toast('✅ Template created!');
      }

      // Reset
      setTempName('');
      setTempDesc('');
      setTempAmount('');
      setTempReceiptRequired(true);
      setEditingTemplateId(null);
      onRefresh();
    } catch (err) {
      toast('❌ Failed to save template: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  const handleEditTemplateClick = (t: ExpenseTemplate) => {
    setEditingTemplateId(t.id);
    setTempName(t.name);
    setTempCategory(t.category);
    setTempDesc(t.description || '');
    setTempAmount(t.amount.toString());
    setTempReceiptRequired(t.receiptRequired);
  };

  const handleDeleteTemplate = async (id: number) => {
    if (!confirm('Delete this template?')) return;
    setLoading(true);
    try {
      await api.templates.delete(id);
      toast('🗑 Template deleted.');
      onRefresh();
    } catch (err) {
      toast('❌ Error: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  // --- User Handlers ---
  const handleCreateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newUserName.trim() || !newUserEmail.trim()) return;
    setLoading(true);
    try {
      await api.users.create({
        name: newUserName.trim(),
        email: newUserEmail.trim(),
        role: newUserRole,
      });
      setNewUserName('');
      setNewUserEmail('');
      toast('👤 New user authorized!');
      fetchUsers();
    } catch (err) {
      toast('❌ Failed to add user: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  const handleToggleUserStatus = async (id: string, currentStatus: UserStatus) => {
    const nextStatus: UserStatus = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    setLoading(true);
    try {
      await api.users.updateStatus(id, nextStatus);
      toast(`👤 User status set to ${nextStatus.toLowerCase()}`);
      fetchUsers();
    } catch (err) {
      toast('❌ Failed to toggle status: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  // --- Threshold Handlers ---
  const handleSaveThreshold = async (e: React.FormEvent) => {
    e.preventDefault();
    const val = parseFloat(thresholdInput);
    if (isNaN(val) || val < 0) return toast('⚠️ Enter a valid positive number');
    setLoading(true);
    try {
      await api.transactions.updateThreshold(val);
      toast('✅ Alert threshold saved!');
      onRefresh();
    } catch (err) {
      toast('❌ Failed to update threshold: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-xl flex flex-col gap-6">
      {/* Sub tabs nav bar */}
      <div className="flex border-b border-slate-800 pb-3 gap-2 flex-wrap">
        <button
          onClick={() => setActiveTab('categories')}
          className={`py-2 px-4 rounded-lg text-xs font-bold transition ${
            activeTab === 'categories' ? 'bg-indigo-600/20 text-indigo-400 border border-indigo-500/30' : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          📂 Expense Categories
        </button>
        <button
          onClick={() => setActiveTab('templates')}
          className={`py-2 px-4 rounded-lg text-xs font-bold transition ${
            activeTab === 'templates' ? 'bg-indigo-600/20 text-indigo-400 border border-indigo-500/30' : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          📝 Expense Templates
        </button>
        <button
          onClick={() => setActiveTab('users')}
          className={`py-2 px-4 rounded-lg text-xs font-bold transition ${
            activeTab === 'users' ? 'bg-indigo-600/20 text-indigo-400 border border-indigo-500/30' : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          👤 User Directory
        </button>
        <button
          onClick={() => setActiveTab('threshold')}
          className={`py-2 px-4 rounded-lg text-xs font-bold transition ${
            activeTab === 'threshold' ? 'bg-indigo-600/20 text-indigo-400 border border-indigo-500/30' : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          🔔 Threshold Warning
        </button>
      </div>

      {/* Categories configuration */}
      {activeTab === 'categories' && (
        <div className="flex flex-col gap-6">
          <div>
            <h4 className="text-sm font-bold text-white mb-1">Expense Categories Tree</h4>
            <p className="text-xs text-slate-400">Configure allowable expense categories and subcategories branches.</p>
          </div>

          <form onSubmit={handleAddCategory} className="flex gap-2">
            <input
              type="text"
              placeholder="Add new Category (e.g. Office Travel)"
              value={newCatName}
              onChange={(e) => setNewCatName(e.target.value)}
              className="bg-slate-950 border border-slate-800 rounded-lg py-2 px-3 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-indigo-500 flex-1"
            />
            <button
              type="submit"
              disabled={loading}
              className="bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs px-4 py-2 rounded-lg transition"
            >
              Add Category
            </button>
          </form>

          {/* Categories Grid List */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {categories.map((cat) => (
              <div key={cat.id} className="bg-slate-950/80 border border-slate-800 rounded-xl p-4 flex flex-col gap-3">
                <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                  {editingCategoryId === cat.id ? (
                    <div className="flex gap-1.5 items-center flex-1 mr-2">
                      <input
                        type="text"
                        value={editingCategoryName}
                        onChange={(e) => setEditingCategoryName(e.target.value)}
                        className="bg-slate-900 border border-slate-750 rounded py-0.5 px-2 text-xs text-white placeholder-slate-700 focus:outline-none focus:border-indigo-500 flex-1"
                      />
                      <button
                        onClick={() => handleUpdateCategory(cat.id)}
                        className="text-[10px] bg-emerald-950/40 text-emerald-400 hover:bg-emerald-950/80 border border-emerald-900/60 rounded px-1.5 py-0.5"
                      >
                        Save
                      </button>
                      <button
                        onClick={() => setEditingCategoryId(null)}
                        className="text-[10px] bg-slate-800 text-slate-300 hover:bg-slate-750 border border-slate-700 rounded px-1.5 py-0.5"
                      >
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <span className="font-bold text-slate-200 text-xs flex items-center gap-1.5">
                      {cat.name}
                      <button
                        onClick={() => {
                          setEditingCategoryId(cat.id);
                          setEditingCategoryName(cat.name);
                        }}
                        className="text-slate-500 hover:text-indigo-400 text-[10px] cursor-pointer"
                        title="Edit Category Name"
                      >
                        ✏️
                      </button>
                    </span>
                  )}
                  {editingCategoryId !== cat.id && (
                    <button
                      onClick={() => handleDeleteCategory(cat.id, cat.name)}
                      className="text-[10px] bg-red-950/40 text-red-400 hover:bg-red-950/80 border border-red-900/60 rounded px-1.5 py-0.5"
                    >
                      Delete Category
                    </button>
                  )}
                </div>

                {/* Subcategories list */}
                <div className="flex flex-col gap-1.5 min-h-[40px]">
                  {cat.subcategories.map((sub) => (
                    <div key={sub.id} className="flex items-center justify-between pl-2 text-xs text-slate-400">
                      {editingSubcategoryId === sub.id ? (
                        <div className="flex gap-1.5 items-center flex-1 mr-2 my-0.5">
                          <input
                            type="text"
                            value={editingSubcategoryName}
                            onChange={(e) => setEditingSubcategoryName(e.target.value)}
                            className="bg-slate-900 border border-slate-800 rounded py-0.5 px-2 text-[11px] text-white focus:outline-none focus:border-indigo-500 flex-1"
                          />
                          <button
                            onClick={() => handleUpdateSubcategory(sub.id)}
                            className="text-[9px] bg-emerald-950/40 text-emerald-400 hover:bg-emerald-950/80 border border-emerald-900/60 rounded px-1 py-0.5"
                          >
                            Save
                          </button>
                          <button
                            onClick={() => setEditingSubcategoryId(null)}
                            className="text-[9px] bg-slate-800 text-slate-300 hover:bg-slate-750 border border-slate-700 rounded px-1 py-0.5"
                          >
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <span className="flex items-center gap-1.5 flex-1">
                          <span className="text-slate-600">•</span> {sub.name}
                          <button
                            onClick={() => {
                              setEditingSubcategoryId(sub.id);
                              setEditingSubcategoryName(sub.name);
                            }}
                            className="text-slate-500 hover:text-indigo-400 text-[10px] ml-1.5 cursor-pointer"
                            title="Edit Subcategory Name"
                          >
                            ✏️
                          </button>
                        </span>
                      )}
                      {editingSubcategoryId !== sub.id && (
                        <button
                          onClick={() => handleDeleteSubcategory(sub.id)}
                          className="text-slate-600 hover:text-red-400 text-[10px]"
                        >
                          ✕
                        </button>
                      )}
                    </div>
                  ))}
                  {cat.subcategories.length === 0 && (
                    <div className="text-[11px] text-slate-600 pl-2">No subcategories defined yet.</div>
                  )}
                </div>

                {/* Inline add subcategory form */}
                <div className="flex gap-1.5 pt-2 border-t border-slate-800/40">
                  <input
                    type="text"
                    placeholder="New Subcategory (e.g. Bus)"
                    value={newSubNames[cat.id] || ''}
                    onChange={(e) =>
                      setNewSubNames((prev) => ({ ...prev, [cat.id]: e.target.value }))
                    }
                    className="bg-slate-950 border border-slate-850 rounded py-1.5 px-2 text-[11px] text-white placeholder-slate-700 focus:outline-none focus:border-indigo-500 flex-1"
                  />
                  <button
                    onClick={() => handleAddSubcategory(cat.id)}
                    className="bg-slate-800 hover:bg-slate-750 text-slate-200 font-semibold text-[11px] px-2.5 rounded transition"
                  >
                    Add
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Templates configuration */}
      {activeTab === 'templates' && (
        <div className="flex flex-col gap-6">
          <div>
            <h4 className="text-sm font-bold text-white mb-1">Expense Templates Manager</h4>
            <p className="text-xs text-slate-400">Pre-configure recurring expenses details for fast autofills.</p>
          </div>

          <form onSubmit={handleSaveTemplate} className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex flex-col gap-4">
            <div className="text-xs font-bold text-indigo-400 border-b border-slate-800 pb-2">
              {editingTemplateId ? '✏️ Edit Template' : '➕ Create New Reusable Template'}
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1">
                <label className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">Template Name</label>
                <input
                  type="text"
                  placeholder="e.g. Weekly Milk Purchase"
                  value={tempName}
                  onChange={(e) => setTempName(e.target.value)}
                  className="bg-slate-900 border border-slate-800 rounded-lg py-1.5 px-3 text-xs text-white focus:outline-none focus:border-indigo-500"
                />
              </div>
              <div className="flex flex-col gap-1">
                <label className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">Category Name (matching text)</label>
                <select
                  value={tempCategory}
                  onChange={(e) => setTempCategory(e.target.value)}
                  className="bg-slate-900 border border-slate-800 rounded-lg py-1.5 px-3 text-xs text-white focus:outline-none focus:border-indigo-500"
                >
                  <option value="">-- Select Category Target --</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.name}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1">
                <label className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">Default Amount (₹)</label>
                <input
                  type="number"
                  placeholder="0.00"
                  value={tempAmount}
                  onChange={(e) => setTempAmount(e.target.value)}
                  className="bg-slate-900 border border-slate-800 rounded-lg py-1.5 px-3 text-xs text-white focus:outline-none focus:border-indigo-500"
                />
              </div>
              <div className="flex flex-col gap-1 justify-center">
                <label className="flex items-center gap-2 cursor-pointer text-xs text-slate-400">
                  <input
                    type="checkbox"
                    checked={tempReceiptRequired}
                    onChange={(e) => setTempReceiptRequired(e.target.checked)}
                    className="rounded bg-slate-900 border-slate-800 text-indigo-600 focus:ring-indigo-900/50"
                  />
                  Receipt Attachment Required
                </label>
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <label className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">Description</label>
              <input
                type="text"
                placeholder="e.g. purchase of 10L fresh milk for office refreshments"
                value={tempDesc}
                onChange={(e) => setTempDesc(e.target.value)}
                className="bg-slate-900 border border-slate-800 rounded-lg py-1.5 px-3 text-xs text-white focus:outline-none focus:border-indigo-500"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-slate-800/40">
              {editingTemplateId && (
                <button
                  type="button"
                  onClick={() => {
                    setEditingTemplateId(null);
                    setTempName('');
                    setTempCategory('');
                    setTempDesc('');
                    setTempAmount('');
                    setTempReceiptRequired(true);
                  }}
                  className="bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-300 font-bold text-xs px-4 py-2 rounded-lg transition"
                >
                  Cancel
                </button>
              )}
              <button
                type="submit"
                disabled={loading}
                className="bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs px-4 py-2 rounded-lg transition"
              >
                {editingTemplateId ? 'Update Template' : 'Create Template'}
              </button>
            </div>
          </form>

          {/* Templates Grid List */}
          <div className="flex flex-col gap-2">
            {templates.map((t) => (
              <div key={t.id} className="bg-slate-950 border border-slate-800 rounded-xl p-3 flex justify-between items-center text-xs">
                <div className="flex flex-col">
                  <span className="font-bold text-slate-200">{t.name}</span>
                  <span className="text-[10px] text-slate-500">
                    Category: {t.category} · Amount: ₹{t.amount} · Receipt:{' '}
                    {t.receiptRequired ? 'Required' : 'Optional'}
                  </span>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => handleEditTemplateClick(t)}
                    className="text-slate-400 hover:text-indigo-400 text-xs px-2"
                  >
                    ✏️
                  </button>
                  <button
                    onClick={() => handleDeleteTemplate(t.id)}
                    className="text-slate-400 hover:text-red-400 text-xs px-2"
                  >
                    🗑
                  </button>
                </div>
              </div>
            ))}
            {templates.length === 0 && (
              <div className="text-center text-slate-500 text-xs py-4">No reusable templates created yet.</div>
            )}
          </div>
        </div>
      )}

      {/* Allowed Users directory */}
      {activeTab === 'users' && (
        <div className="flex flex-col gap-6">
          <div>
            <h4 className="text-sm font-bold text-white mb-1">User Access Directory</h4>
            <p className="text-xs text-slate-400">Authorize employee emails and define access roles (ADMIN vs USER).</p>
          </div>

          <form onSubmit={handleCreateUser} className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex flex-col gap-3">
            <div className="text-xs font-bold text-indigo-400 border-b border-slate-800 pb-2">
              ➕ Authorize New User Login
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <input
                type="text"
                placeholder="Full Name (e.g. Tony Stark)"
                required
                value={newUserName}
                onChange={(e) => setNewUserName(e.target.value)}
                className="bg-slate-900 border border-slate-850 rounded-lg py-1.5 px-3 text-xs text-white focus:outline-none focus:border-indigo-500"
              />
              <input
                type="email"
                placeholder="Google Email (e.g. tony@example.com)"
                required
                value={newUserEmail}
                onChange={(e) => setNewUserEmail(e.target.value)}
                className="bg-slate-900 border border-slate-850 rounded-lg py-1.5 px-3 text-xs text-white focus:outline-none focus:border-indigo-500"
              />
              <select
                value={newUserRole}
                onChange={(e) => setNewUserRole(e.target.value as Role)}
                className="bg-slate-900 border border-slate-855 rounded-lg py-1.5 px-3 text-xs text-white focus:outline-none focus:border-indigo-500"
              >
                <option value="USER">USER role (expenses only)</option>
                <option value="ADMIN">ADMIN role (expenses + settings)</option>
              </select>
            </div>
            <div className="flex justify-end pt-1">
              <button
                type="submit"
                disabled={loading}
                className="bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs px-4 py-2 rounded-lg transition"
              >
                Grant Access
              </button>
            </div>
          </form>

          {/* User Table Grid */}
          <div className="bg-slate-950 border border-slate-800 rounded-xl overflow-hidden">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="bg-slate-900 border-b border-slate-800 text-slate-400">
                  <th className="p-3">Name</th>
                  <th className="p-3">Email</th>
                  <th className="p-3">Role</th>
                  <th className="p-3">Status</th>
                  <th className="p-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {usersList.map((user) => (
                  <tr key={user.id} className="border-b border-slate-900 hover:bg-slate-900/30">
                    <td className="p-3 font-semibold text-slate-200">{user.name}</td>
                    <td className="p-3 text-slate-400">{user.email}</td>
                    <td className="p-3">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                        user.role === 'ADMIN' ? 'bg-indigo-950 text-indigo-400 border border-indigo-900' : 'bg-slate-900 text-slate-400'
                      }`}>
                        {user.role}
                      </span>
                    </td>
                    <td className="p-3">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                        user.status === 'ACTIVE' ? 'bg-emerald-950 text-emerald-400' : 'bg-red-950 text-red-400'
                      }`}>
                        {user.status}
                      </span>
                    </td>
                    <td className="p-3 text-right">
                      {user.email.toLowerCase() !== currentUser.email.toLowerCase() ? (
                        <button
                          onClick={() => handleToggleUserStatus(user.id, user.status)}
                          className={`text-[10px] font-bold border rounded px-2 py-0.5 transition ${
                            user.status === 'ACTIVE'
                              ? 'border-red-900 text-red-400 hover:bg-red-950/40'
                              : 'border-emerald-900 text-emerald-400 hover:bg-emerald-950/40'
                          }`}
                        >
                          {user.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                        </button>
                      ) : (
                        <span className="text-[10px] text-slate-600 font-medium">Self</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Threshold Low Balance alerts */}
      {activeTab === 'threshold' && (
        <div className="flex flex-col gap-6">
          <div>
            <h4 className="text-sm font-bold text-white mb-1">Low Balance Threshold Settings</h4>
            <p className="text-xs text-slate-400">Configure warning limits for available cash-box drawer balance.</p>
          </div>

          <form onSubmit={handleSaveThreshold} className="bg-slate-950 border border-slate-800 rounded-xl p-5 flex flex-col gap-4 max-w-md">
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Low Balance Threshold Value (₹)
              </label>
              <input
                type="number"
                min="0"
                value={thresholdInput}
                onChange={(e) => setThresholdInput(e.target.value)}
                className="bg-slate-900 border border-slate-800 rounded-lg py-2 px-3 text-xs text-white focus:outline-none focus:border-indigo-500"
              />
            </div>
            <p className="text-[11px] text-slate-500">
              When the active cashbox balance goes below this amount, a glowing warning card will be displayed on the main dashboard tab.
            </p>
            <div className="flex justify-end mt-2">
              <button
                type="submit"
                disabled={loading}
                className="bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs px-4 py-2 rounded-lg transition"
              >
                Save Threshold
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
