function analyzeQuery() {
  alert('Query analysis initiated! (Demo)');
}

function switchTab(e, tabName) {
  const tabs = document.querySelectorAll('.veil-tab');
  const contents = document.querySelectorAll('.veil-tab-content');
  
  tabs.forEach(tab => tab.classList.remove('active'));
  contents.forEach(content => content.classList.remove('active'));
  
  e.target.classList.add('active');
  document.getElementById(tabName).classList.add('active');
}

document.addEventListener('keydown', (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    analyzeQuery();
  }
});