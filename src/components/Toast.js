import React from 'react';

export default function Toast({ toastState }) {
  return (
    <div id="toast" className={toastState.show ? 'show' : ''}>
      {toastState.msg}
    </div>
  );
}
