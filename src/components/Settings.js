import React, { useState, useEffect, useRef } from 'react';

export default function Settings({ state, addCat, rmCat, addMem, rmMem, saveThresh, wipeAll, onExportJSON, onImportJSON }) {
  const [newCat, setNewCat] = useState('');
  const [newMem, setNewMem] = useState('');
  const [thresh, setThresh] = useState(state.lowThreshold);
  const jsonRef = useRef();

  useEffect(() => setThresh(state.lowThreshold), [state.lowThreshold]);

  const handleAddCat = () => { addCat(newCat); setNewCat(''); };
  const handleAddMem = () => { addMem(newMem); setNewMem(''); };

  const handleJsonChange = e => {
    if (e.target.files[0]) {
      onImportJSON(e.target.files[0]);
      e.target.value = '';
    }
  };

  return (
    <section>
      <div className="page-head">
        <div><h2>Settings</h2><p>Categories, team members and data backup.</p></div>
      </div>

      <div className="grid-2">
        <div className="card">
          <h3>Expense Categories</h3>
          <div className="card-sub">Used for tagging expenses and dashboard insights</div>
          <div className="pill-list">
            {state.categories.map((c, i) => (
              <span key={c} className="pill">
                {c}
                <button onClick={() => rmCat(i)} title="Remove">✕</button>
              </span>
            ))}
          </div>
          <div className="add-inline">
            <input
              value={newCat}
              onChange={e => setNewCat(e.target.value)}
              placeholder="New category e.g. Pantry"
              className="flt"
              onKeyDown={e => { if (e.key === 'Enter') handleAddCat(); }}
            />
            <button className="btn btn-primary btn-sm" onClick={handleAddCat}>Add</button>
          </div>
        </div>

        <div className="card">
          <h3>Team Members</h3>
          <div className="card-sub">People who can pay from / handle the cash box</div>
          <div className="pill-list">
            {state.members.map((m, i) => (
              <span key={m} className="pill">
                {m}
                <button onClick={() => rmMem(i)} title="Remove">✕</button>
              </span>
            ))}
          </div>
          <div className="add-inline">
            <input
              value={newMem}
              onChange={e => setNewMem(e.target.value)}
              placeholder="New member e.g. Priya"
              className="flt"
              onKeyDown={e => { if (e.key === 'Enter') handleAddMem(); }}
            />
            <button className="btn btn-primary btn-sm" onClick={handleAddMem}>Add</button>
          </div>
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <h3>Low Balance Alert</h3>
          <div className="card-sub">Dashboard warns when cash in hand falls below this</div>
          <div className="add-inline">
            <input
              type="number"
              min="0"
              className="flt"
              value={thresh}
              onChange={e => setThresh(e.target.value)}
            />
            <button className="btn btn-primary btn-sm" onClick={() => saveThresh(parseFloat(thresh))}>Save</button>
          </div>
        </div>

        <div className="card">
          <h3>Backup &amp; Restore</h3>
          <div className="card-sub">Full backup includes settings; CSV is for Excel</div>
          <div className="head-actions">
            <button className="btn btn-ghost" onClick={onExportJSON}>⬇ Download Backup (.json)</button>
            <button className="btn btn-ghost" onClick={() => jsonRef.current.click()}>⬆ Restore Backup</button>
            <button className="btn btn-ghost" style={{ color: 'var(--red)' }} onClick={wipeAll}>🗑 Erase All Data</button>
          </div>
          <input type="file" ref={jsonRef} accept=".json" style={{ display: 'none' }} onChange={handleJsonChange} />
        </div>
      </div>
    </section>
  );
}
