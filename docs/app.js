// PostgreSQL DDL Studio Landing Page Logic

document.addEventListener('DOMContentLoaded', () => {
    // 1. Theme Toggle
    const themeBtn = document.getElementById('themeToggle');
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    let currentTheme = localStorage.getItem('site-theme') || (prefersDark ? 'dark' : 'light');

    document.documentElement.setAttribute('data-theme', currentTheme);
    updateThemeBtnText();

    themeBtn?.addEventListener('click', () => {
        currentTheme = currentTheme === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', currentTheme);
        localStorage.setItem('site-theme', currentTheme);
        updateThemeBtnText();
    });

    function updateThemeBtnText() {
        if (themeBtn) {
            themeBtn.textContent = currentTheme === 'dark' ? 'Açık Tema' : 'Koyu Tema';
        }
    }

    // 2. Interactive Mockup Tabs
    const tabBtns = document.querySelectorAll('.tab-btn');
    const tabContents = document.querySelectorAll('.tab-content');

    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            tabBtns.forEach(b => b.classList.remove('active'));
            tabContents.forEach(c => c.classList.remove('active'));

            btn.classList.add('active');
            const targetId = btn.getAttribute('data-tab');
            const targetContent = document.getElementById(targetId);
            if (targetContent) {
                targetContent.classList.add('active');
            }
        });
    });

    // 3. Copy CLI Command Button
    const copyBtn = document.getElementById('copyCliBtn');
    copyBtn?.addEventListener('click', () => {
        const cmd = 'java -jar postgres_ddl_export_console_java-1.0.0.jar -db:POSTGRESQL -od:./export_output -s:profiles.json';
        navigator.clipboard.writeText(cmd).then(() => {
            copyBtn.textContent = 'Kopyalandı!';
            setTimeout(() => {
                copyBtn.textContent = 'Kopyala';
            }, 2000);
        });
    });
});
