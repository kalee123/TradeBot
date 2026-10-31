// T+1 shares (bought yesterday, not yet settled) are reported separately by Kite
function holdingQty(h) {
    return (Number(h.quantity) || 0) + (Number(h.t1_quantity) || 0);
}

function renderSummary(holdings) {
    let invested = 0;
    let current = 0;
    holdings.forEach(h => {
        invested += holdingQty(h) * (Number(h.average_price) || 0);
        current += holdingQty(h) * (Number(h.last_price) || 0);
    });
    const pnl = current - invested;
    const pnlPct = invested ? (pnl / invested) * 100 : 0;

    document.getElementById("total-invested").textContent = money(invested);
    document.getElementById("total-current").textContent = money(current);
    const pnlEl = document.getElementById("total-pnl");
    pnlEl.className = "big " + pnlClass(pnl);
    pnlEl.textContent = `${money(pnl)} (${percent(pnlPct)})`;
}

loadPage(async () => {
    const holdings = await api("/api/holdings");
    renderSummary(holdings);

    document.getElementById("holdings-body").innerHTML = tableRows(holdings, 7, "No holdings", h => {
        const qty = holdingQty(h);
        return `
            <tr>
                <td><span class="symbol">${escapeHtml(h.tradingsymbol)}</span> <span class="muted">${escapeHtml(h.exchange)}</span></td>
                <td class="num">${number(qty)}</td>
                <td class="num">${money(h.average_price)}</td>
                <td class="num">${money(h.last_price)}</td>
                <td class="num">${money(qty * (Number(h.last_price) || 0))}</td>
                <td class="num ${pnlClass(h.pnl)}">${money(h.pnl)}</td>
                <td class="num ${pnlClass(h.day_change_percentage)}">${percent(h.day_change_percentage)}</td>
            </tr>`;
    });
});
