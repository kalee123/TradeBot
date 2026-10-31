loadPage(async () => {
    // Kite returns { net: [...], day: [...] }; "net" is the overall position across days
    const data = await api("/api/positions");
    const positions = data.net || [];

    const totalPnl = positions.reduce((sum, p) => sum + (Number(p.pnl) || 0), 0);
    const open = positions.filter(p => Number(p.quantity) !== 0).length;

    const pnlEl = document.getElementById("total-pnl");
    pnlEl.className = "big " + pnlClass(totalPnl);
    pnlEl.textContent = money(totalPnl);
    document.getElementById("open-count").textContent = open;

    document.getElementById("positions-body").innerHTML = tableRows(positions, 6, "No positions", p => `
        <tr class="${Number(p.quantity) === 0 ? "muted" : ""}">
            <td>${escapeHtml(p.product)}</td>
            <td><span class="symbol">${escapeHtml(p.tradingsymbol)}</span> <span class="muted">${escapeHtml(p.exchange)}</span></td>
            <td class="num ${pnlClass(p.quantity)}">${number(p.quantity)}</td>
            <td class="num">${money(p.average_price)}</td>
            <td class="num">${money(p.last_price)}</td>
            <td class="num ${pnlClass(p.pnl)}">${money(p.pnl)}</td>
        </tr>`);
});
