function renderProfile(p) {
    const chips = (p.exchanges || []).map(e => `<span class="chip">${escapeHtml(e)}</span>`).join("");
    document.getElementById("profile").innerHTML = `
        <h2>Profile</h2>
        <div class="big">${escapeHtml(p.user_name)}</div>
        <div class="muted">${escapeHtml(p.email)}</div>
        <dl>
            <dt>User ID</dt><dd>${escapeHtml(p.user_id)}</dd>
            <dt>Broker</dt><dd>${escapeHtml(p.broker)}</dd>
            <dt>Account type</dt><dd>${escapeHtml(p.user_type)}</dd>
        </dl>
        <p class="label" style="margin-top:14px">Exchanges</p>
        <div class="chips">${chips || '<span class="muted">None</span>'}</div>`;
}

function renderSegment(elementId, title, seg) {
    const el = document.getElementById(elementId);
    if (!seg || seg.enabled === false) {
        el.innerHTML = `<h2>${title}</h2><p class="muted">Segment not enabled</p>`;
        return;
    }
    const available = seg.available || {};
    const utilised = seg.utilised || {};
    el.innerHTML = `
        <h2>${title} margin</h2>
        <p class="label">Available</p>
        <div class="big">${money(seg.net)}</div>
        <dl>
            <dt>Cash</dt><dd>${money(available.cash)}</dd>
            <dt>Opening balance</dt><dd>${money(available.opening_balance)}</dd>
            <dt>Collateral</dt><dd>${money(available.collateral)}</dd>
            <dt>Used (debits)</dt><dd>${money(utilised.debits)}</dd>
            <dt>Unrealised M2M</dt><dd class="${pnlClass(utilised.m2m_unrealised)}">${money(utilised.m2m_unrealised)}</dd>
        </dl>`;
}

loadPage(async () => {
    const data = await api("/api/dashboard");
    const margins = data.margin_details || {};
    renderProfile(data.profile_details || {});
    renderSegment("equity", "Equity", margins.equity);
    renderSegment("commodity", "Commodity", margins.commodity);
});
