const renderer = new marked.Renderer();

window.HIPATIA_DEBUG = false;

const log = {
    info:  (...args) => HIPATIA_DEBUG && console.log(...args),
    warn:  (...args) => HIPATIA_DEBUG && console.warn(...args),
    error: (...args) => console.error(...args)  // errores reales siempre visibles
};

// Si la respuesta tiene enlaces, estos se abren en un nuevo navegador
renderer.link = function({ href, title, text }) {
    const titleAttr = title ? `title="${title}"` : '';
    // Forzamos el target="_blank" y añadimos rel para seguridad
    return `<a href="${href}" ${titleAttr} target="_blank" rel="noopener noreferrer">${text}</a>`;
};

// Markdowm, omitimos código wrapper
renderer.code = function({ text, lang }) {
    // Si el contenido del bloque de código tiene el símbolo de tabla '|'
    // lo renderizamos como markdown normal, no como bloque de código.
    if (text.includes('|')) {
        console.warn("Tabla markdown");
        const html = marked.parse(text);
        return html.replace('<table>', '<table class="meiib-table">');
    }

    // Si es código de verdad (java, sql...), devolvemos el formato estándar
    return `<pre><code class="language-${lang || ''}">${text}</code></pre>`;
};

// Genera un id único para cada gráfico generado en la sesión
function generarIdUnico(str) {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
        hash = ((hash << 5) - hash) + str.charCodeAt(i);
        hash |= 0;
    }
    return Math.abs(hash); // Devuelve un número positivo único
}

// Aplicamos el renderizador personalizado
marked.use({ renderer });

async function iniciarChatStreaming() {
    const inputWidget = PF('inputUsuariWidget');
    const textUsuari = inputWidget.jq.val();
    const chatHistory = document.getElementById('chatHistory');

    if (!textUsuari.trim()) return; // Evitam enviament buit!.

    const userDiv = document.createElement('div');
    userDiv.className = 'user-bubble';
    userDiv.innerText = textUsuari;
    chatHistory.appendChild(userDiv);
    inputWidget.jq.val('');

    // Afegim bimbolles d'espera
    const aiDiv = document.createElement('div');
    aiDiv.className = 'ai-bubble';
    aiDiv.innerHTML='<div class="typing"><span></span><span></span><span></span></div>'
    chatHistory.appendChild(aiDiv);

    chatHistory.scrollTop = chatHistory.scrollHeight;



    try {
        const response = await fetch('rest/chat/stream', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json'},
            body: textUsuari
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder("utf-8");

        let primerToken = true;
        let acumulado = "";
        let isToolchain = false;

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            const chunk = decoder.decode(value, { stream: true });

            if (primerToken) {
                if (chunk.includes("<message>")) {
                    isToolchain = true;
                    const regex = /<message>(.*?)<\/message>/;
                    const match = chunk.match(regex);
                    const methodName = match ? match[1] : "ToolChain calling";

                    aiDiv.innerHTML = `
                    <div class="fetching dog-search">
                        <span class="dog">🐕</span>
                        <span class="fetching-text">Ejecutando: ${methodName}</span>
                        <div class="dots-container">
                            <span class="dot"></span>
                            <span class="dot"></span>
                            <span class="dot"></span>
                        </div>
                    </div>`;
                }
            }
            else {
                aiDiv.innerHTML = '';
                primerToken = false;
            }

            if (!isToolchain) {
                acumulado += chunk;
                renderizarMarkdownYGraficos(acumulado, aiDiv, false);
            } else {
                // Si el chunk ya no trae la etiqueta, pasamos al modo texto normal
                if (!chunk.includes('<message>')) {
                    isToolchain = false;
                    primerToken = false;
                    acumulado += chunk;
                    renderizarMarkdownYGraficos(acumulado, aiDiv, false);
                }
            }
        }
        renderizarMarkdownYGraficos(acumulado, aiDiv, true);
        chatHistory.scrollTop = chatHistory.scrollHeight;

    } catch (error) {
        console.error("Error en el streaming:", error);
        aiDiv.innerHTML += "<br/><b>[Error de conexión con el asistente]</b>: " + error.message;
    }
}


function renderizarMarkdownYGraficos(textoAcumulado, contenedorDOM, esFinDeStream = false) {

    //let htmlFinal = marked.parse(textoAcumulado).replace(/<table>/g, '<table class="meiib-table">'); // si el LLM genera tablas, le ponemos nuestro estilo css
    let htmlFinal = marked.parse(textoAcumulado)
        .replace(/<table>/g,
            `<div class="meiib-table-wrapper">
                 <button type="button" class="meiib-copy-btn" title="Copiar tabla">
                     <i class="pi pi-copy"></i>
                 </button>
                 <table class="meiib-table">`
        )
        .replace(/<\/table>/g, '</table></div>')
    let graficosParaPintar = [];

    // Si en la respuesta el LLM nos ha generado un gráfico <meiib-graphic>
    // vamos a poner espera de generación de gráfico hasta recibir el último token
    // que nos permitirá renderizarlo correctamente. A diferencia del ToolChain donde el LLM
    // nos pide llamar a una función y por ende ponemos la espera (perrito olisqueando)
    // en el caso de los gráficos, el JSON del gráfico puede venir entre texto normal de respuesta, por este motivo lo tratamos aquí.
    // caso en el que todo el gráfico nos viene completo en una sola respuesta
    const regexGraficoCompleto = /<meiib-graphic>([\s\S]*?)<\/meiib-graphic>/g;

    htmlFinal = htmlFinal.replace(regexGraficoCompleto, (match, jsonString) => {

            let cleanJson = jsonString.replace(/&quot;/g, '"').trim(); // Decodificamos posibles caracteres de escape HTML que ponga marked.js
            const canvasId = `meiib-chart-canvas-${generarIdUnico(cleanJson)}`; // Generamos id único para cada gráfico
            graficosParaPintar.push({ id: canvasId, json: cleanJson });

            let tipo = 'bar';
            try { tipo = JSON.parse(cleanJson).type || 'bar'; } catch(e) {}
            const altura = (tipo === 'pie' || tipo === 'doughnut') ? '65vh' : '50vh'

            if (!esFinDeStream) {
                return `<div class="chart-container" style="height:40vh; width:100%; display:flex; align-items:center; justify-content:center; background:#f8f9fa; border:2px dashed #dee2e6; border-radius: 8px;">
                           <span style="color:#6c757d; font-weight: bold;">📊 Gráfico generado. Esperando finalización del texto...</span>
                        </div>`;
            }

            return `<div class="chart-container" style="position: relative; height:${altura}; width:100%; min-height:400px;">
                        <button type="button" class="meiib-config-btn meiib-config-btn--chart" data-canvas-id="${canvasId}" title="Configurar gráfico" style="position: absolute; top: 5px; right: 45px;"><i class="pi pi-cog"></i></button>
                        <button type="button" class="meiib-copy-btn meiib-copy-btn--chart" title="Copiar gráfico"><i class="pi pi-copy"></i></button>
                        <canvas id="${canvasId}"></canvas>
                     </div>`;
    });

    // caso en el que nos viene la abertura de etiqueta de gráfico pero no su cierre (porqué el streaming aún no ha acabado)
    const regexGraficoIncompleto = /<meiib-graphic>([\s\S]*)$/;
    if (regexGraficoIncompleto.test(htmlFinal)) {
        htmlFinal = htmlFinal.replace(regexGraficoIncompleto, `
            <div class="fetching">
                <span class="dog">📊</span>
                <span class="fetching-text">Generando gráfico interactivo</span>
                <div class="dots-container"><span class="dot"></span><span class="dot"></span><span class="dot"></span></div>
            </div>`);
    }

    // Inyectamos el resultado al DOM
    contenedorDOM.innerHTML = htmlFinal;
    initConfigButtons();

    // Instanciamos Chart.js
    if (esFinDeStream) {
        window.requestAnimationFrame(() => {
            graficosParaPintar.forEach(grafico => {
                try {
                    const configObj = JSON.parse(grafico.json);
                    const ctx = document.getElementById(grafico.id);

                    let existingChart = Chart.getChart(grafico.id);
                    if (existingChart) existingChart.destroy();

                    if (!configObj.options) configObj.options = {};

                    applyFormatters(configObj);

                    agregarPluginTotalCentrado(configObj);

                    // Registrar plugin datalabels
                    if (Chart && ChartDataLabels) {
                        Chart.register(ChartDataLabels);
                    }

                    const chart = new Chart(ctx, configObj);

                    setTimeout(() => {
                        if (chart && chart.options && chart.options.plugins) {
                            configurarTitulo(chart, configObj);
                            configurarDatalabels(chart, configObj);

                            chart.update();
                        }
                    }, 200);

                } catch (error) {
                    console.warn("Error al crear gráfico:", error);
                }
            });
        });
    }
}

function rehidratarHistorialJS() {

    const chatHistory = document.getElementById('chatHistory');
    const burbujasIAPendientes = document.querySelectorAll('.unparsed-markdown');

    burbujasIAPendientes.forEach(burbuja => {

        const textoMarkdownCrudo = burbuja.textContent;
        const htmlParseado =renderizarMarkdownYGraficos(textoMarkdownCrudo,burbuja,true)
        burbuja.classList.remove('unparsed-markdown');
    });

    if (chatHistory) {
        chatHistory.scrollTop = chatHistory.scrollHeight;
    }
}

async function copiarTabla(tabla, boton) {

    let exito = false;

    // Primero intentamos execCommand: selecciona el DOM renderizado con estilos
    try {
        const sel = window.getSelection();
        const range = document.createRange();
        range.selectNodeContents(tabla);
        sel.removeAllRanges();
        sel.addRange(range);
        exito = document.execCommand('copy');
        sel.removeAllRanges();
    } catch (err) {
        console.warn('execCommand falló, usando clipboard.write:', err.message);
    }

    // Fallback: clipboard.write() con HTML crudo
    if (!exito && navigator.clipboard && window.ClipboardItem) {
        try {
            const htmlBlob = `<html><body>${tabla.outerHTML}</body></html>`;
            const textoPlano = tablaATextoPlano(tabla);
            await navigator.clipboard.write([
                new ClipboardItem({
                    'text/html' : new Blob([htmlBlob],   { type: 'text/html'  }),
                    'text/plain': new Blob([textoPlano], { type: 'text/plain' })
                })
            ]);
            exito = true;
        } catch (err) {
            console.error('clipboard.write también falló:', err);
        }
    }
    // Feedback visual en el icono
    const icono = boton.querySelector('i');
    if (exito) {
        icono.className = 'pi pi-check';
        boton.classList.add('meiib-copy-btn--ok');
    } else {
        icono.className = 'pi pi-times';
        boton.classList.add('meiib-copy-btn--err');
    }

    setTimeout(() => {
        icono.className = 'pi pi-copy';
        boton.classList.remove('meiib-copy-btn--ok', 'meiib-copy-btn--err');
    }, 2000);
}

function tablaATextoPlano(tabla) {
    return Array.from(tabla.querySelectorAll('tr'))
        .map(fila =>
            Array.from(fila.querySelectorAll('th, td'))
                .map(c => c.innerText.trim())
                .join('\t')
        ).join('\n');
}

async function copiarGrafico(canvas, boton) {
    let exito = false;

    // HTTPS — clipboard.write() con image/png
    if (navigator.clipboard && window.ClipboardItem) {
        try {
            // canvas.toBlob es asíncrono, lo promisificamos
            const blob = await new Promise((resolve, reject) => {
                canvas.toBlob(b => b ? resolve(b) : reject('toBlob falló'), 'image/png');
            });

            await navigator.clipboard.write([
                new ClipboardItem({ 'image/png': blob })
            ]);
            exito = true;

        } catch (err) {
            console.warn('clipboard.write imagen no disponible, usando fallback:', err.message);
        }
    }

    // Fallback HTTP — div contenteditable con <img> → execCommand
    if (!exito) {
        try {
            const dataUrl = canvas.toDataURL('image/png');

            // Creamos un div temporal editable fuera de la vista
            const tmp = document.createElement('div');
            tmp.contentEditable = true;
            tmp.style.cssText = 'position:fixed; left:-9999px; top:0; opacity:0;';

            const img = document.createElement('img');
            img.src = dataUrl;
            tmp.appendChild(img);
            document.body.appendChild(tmp);

            // Esperamos a que el navegador pinte la imagen antes de seleccionar
            await new Promise(r => setTimeout(r, 100));

            const sel = window.getSelection();
            const range = document.createRange();
            range.selectNodeContents(tmp);
            sel.removeAllRanges();
            sel.addRange(range);
            exito = document.execCommand('copy');
            sel.removeAllRanges();
            document.body.removeChild(tmp);

        } catch (err) {
            console.error('Fallback imagen también falló:', err);
        }
    }

    // Feedback visual (igual que en tablas)
    const icono = boton.querySelector('i');
    icono.className = exito ? 'pi pi-check' : 'pi pi-times';
    boton.classList.add(exito ? 'meiib-copy-btn--ok' : 'meiib-copy-btn--err');
    setTimeout(() => {
        icono.className = 'pi pi-copy';
        boton.classList.remove('meiib-copy-btn--ok', 'meiib-copy-btn--err');
    }, 2000);
}

// Fallback para HTTP / navegadores sin ClipboardItem
function copiarFallback(tabla) {
    const sel = window.getSelection();
    const range = document.createRange();
    range.selectNodeContents(tabla);
    sel.removeAllRanges();
    sel.addRange(range);
    document.execCommand('copy');
    sel.removeAllRanges();
}

function inicializarCopyListeners() {
    document.addEventListener('click', function(e) {
        const btn = e.target.closest('.meiib-copy-btn');
        if (!btn) return;

        e.preventDefault();
        e.stopPropagation();

        if (btn.classList.contains('meiib-copy-btn--chart')) {
            const canvas = btn.closest('.chart-container')?.querySelector('canvas');
            if (canvas) copiarGrafico(canvas, btn);
        } else {
            const tabla = btn.closest('.meiib-table-wrapper')?.querySelector('table.meiib-table');
            if (tabla) copiarTabla(tabla, btn);
        }
    });
}

function applyFormatters(configObj) {

    if (configObj.type === 'bar') {
        const dl = configObj.options?.plugins?.datalabels;
        if (dl) {
            dl.formatter = (value) => Math.round(value / 1000000) + ' M';
            dl.display = (context) => context.dataIndex < 50;
        }
    }

    if (configObj.type === 'pie' || configObj.type === 'doughnut') {
        configObj.options.plugins.datalabels = {
            anchor: 'center',
            align: 'center',
            display: true,
            textAlign: 'center',
            padding: 6,
            color: configObj.options?.plugins?.title?.color || 'rgb(0, 0, 0)',
            font: {
                family: configObj.options?.plugins?.datalabels?.font?.family || "'Segoe UI', 'Helvetica Neue', Arial, Sans-serif",
                size: configObj.options?.plugins?.datalabels?.font?.size || 16,
                weight: configObj.options?.plugins?.datalabels?.font?.weight || 600
            },
            formatter: function(value, context) {
                const data = context.chart.data.datasets[0].data;
                const sum = data.reduce((a, b) => a + b, 0);
                const pct = (value * 100) / sum;
                const pctFormatted = pct.toLocaleString('es-ES', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + '%';
                const millions = new Intl.NumberFormat('es-ES', { useGrouping: true, minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(Math.round(value / 1000000));
                const label = context.chart.data.labels[context.dataIndex];
                return pct > 10 ? label + '\n' + millions + ' M € (' + pctFormatted + ')' : null;
            }
        };

        // Leyenda personalizada
        const legend = configObj.options?.plugins?.legend;
        if (legend) {
            legend.labels = legend.labels || {};
            legend.labels.generateLabels = function(chart) {
                const dataset = chart.data.datasets[0];
                const total = dataset.data.reduce((sum, val) => sum + val, 0);
                return chart.data.labels.map(function(label, i) {
                    const value = dataset.data[i];
                    const millions = new Intl.NumberFormat('es-ES', { useGrouping: true, minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(Math.round(value / 1000000));
                    const pct = ((value * 100) / total).toLocaleString('es-ES', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
                    return {
                        text: label + '  |  ' + millions + ' M €  (' + pct + '%)',
                        fillStyle: dataset.backgroundColor[i],
                        strokeStyle: dataset.borderColor?.[0] || '#fff',
                        lineWidth: 1,
                        hidden: false,
                        index: i
                    };
                });
            };
        }
    }

}

// Función 1: Configurar Título
function configurarTitulo(chart, configObj) {
    const titleFontSize = configObj.options?.plugins?.title?.font?.size || 28;
    const titleFontFamily = 'Arial, sans-serif';
    const chartType = configObj.type || chart.config.type;

    chart.options.plugins.title = {
        display: true,
        text: configObj.options?.plugins?.title?.text || 'Gráfico',
        font: { size: titleFontSize, weight: 'bold', family: titleFontFamily },
        color: configObj.options?.plugins?.title?.color || 'rgb(0,0,0)'
    };

}

// Función 2: Configurar Datalabels (dentro del gráfico)
function configurarDatalabels(chart, configObj) {
     const chartType = configObj.type || chart.config.type;

        if (chartType === 'bar') {
            // Solo para barras: millones encima
            chart.options.plugins.datalabels = {
                anchor: 'end',
                align: 'top',
                offset: 8,
                font: {
                    size: configObj.options?.plugins?.datalabels?.font?.size || 14,
                    weight: 'normal'
                },
                color: configObj.options?.plugins?.datalabels?.color || 'black',
                formatter: function(value) {
                    let numericValue = typeof value === 'number' ? value : parseFloat(String(value).replace(/\./g, '').replace(',', '.'));
                    const millones = numericValue / 1000000;

                    // Formatear con separador de miles y 2 decimales si es necesario
                    const formattedMillions = new Intl.NumberFormat('es-ES', {
                        useGrouping: true,
                        minimumFractionDigits: 0,
                        maximumFractionDigits: 0
                    }).format(millones);

                    return formattedMillions + ' M €';
                }
            };
        }
        else if (chartType === 'pie') {
            // Para pie
            chart.options.plugins.datalabels = {
                font: {
                    size: configObj.options?.plugins?.datalabels?.font?.size || 16,
                    weight: configObj.options?.plugins?.datalabels?.font?.weight || 600
                },
                color: configObj.options?.plugins?.datalabels?.color || 'black',
                formatter: function(value, context) {
                    const data = context.chart.data.datasets[0].data;
                    const sum = data.reduce((a, b) => a + b, 0);
                    const pct = (value * 100) / sum;
                    const pctFormatted = pct.toLocaleString('es-ES', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + '%';
                    const millions = new Intl.NumberFormat('es-ES', { useGrouping: true, minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(Math.round(value / 1000000));
                    const label = context.chart.data.labels[context.dataIndex];
                    return pct > 10 ? label + '\n' + millions + ' M € (' + pctFormatted + ')' : null;
                }
            };
        }
        else if  (chartType === 'doughnut') {
            // Para doughnut
             chart.options.plugins.datalabels = {
                anchor: 'start',
                align: 'end',
                offset: 15,
                radius: 35,
                font: {
                    size: configObj.options?.plugins?.datalabels?.font?.size || 16,
                    weight: configObj.options?.plugins?.datalabels?.font?.weight || 600
                },
                color: configObj.options?.plugins?.datalabels?.color || 'black',
                formatter: function(value, context) {
                    const data = context.chart.data.datasets[0].data;
                    const sum = data.reduce((a, b) => a + b, 0);
                    const pct = (value * 100) / sum;
                    const pctFormatted = pct.toLocaleString('es-ES', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + '%';
                    const millions = new Intl.NumberFormat('es-ES', { useGrouping: true, minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(Math.round(value / 1000000));
                    const label = context.chart.data.labels[context.dataIndex];
                    return pct > 10 ? label + '\n' + millions + ' M € (' + pctFormatted + ')' : null;
                }
            };
        }
}

// Función para agregar plugin de total centrado en doughnut
function agregarPluginTotalCentrado(configObj) {
    if (configObj.type !== 'doughnut') return configObj;

    const formatter = new Intl.NumberFormat('es-ES', {
        useGrouping: true,
        minimumFractionDigits: 0,
        maximumFractionDigits: 0
    });

    const centerTotalPlugin = {
        id: 'centerTotal',
        afterDraw(chart) {
            const { ctx, chartArea, data } = chart;
            const total = data.datasets[0].data.reduce((a, b) => a + b, 0);
            const millones = total / 1000000;
            let totalFormatted = formatter.format(Math.round(millones)) + ' M €';
            let fontSize = millones >= 1000 ? 38 : 42;
            const centerX = (chartArea.left + chartArea.right) / 2;
            const centerY = (chartArea.top + chartArea.bottom) / 2;
            ctx.save();
            ctx.font = `bold ${fontSize}px Arial, sans-serif`;
            ctx.fillStyle = '#333333';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(totalFormatted, centerX, centerY);
            ctx.restore();
        }
    };

    configObj.plugins = configObj.plugins || [];
    configObj.plugins.push(centerTotalPlugin);

    return configObj;
}

// Inicializar listeners para botones de configuración (después de inyectar el HTML)
function initConfigButtons() {
    document.querySelectorAll('.meiib-config-btn').forEach(btn => {
        // Evitar duplicar listeners
        if (btn.hasListener) return;
        btn.hasListener = true;

        btn.addEventListener('click', function() {
            const canvasId = this.getAttribute('data-canvas-id');
            const canvas = document.getElementById(canvasId);
            const chart = Chart.getChart(canvasId);

            if (chart) {
                abrirDialogConfiguracion(canvasId, chart);
            } else {
                console.warn('Gráfico no encontrado:', canvasId);
            }
        });
    });
}

// Función para abrir el diálogo de configuración
function abrirDialogConfiguracion(canvasId, chart) {
    // Eliminar diálogo existente si hay
    const existingDialog = document.getElementById(`dialog-${canvasId}`);
    if (existingDialog) existingDialog.remove();

    // Crear diálogo modal
    const dialog = document.createElement('div');
    dialog.id = `dialog-${canvasId}`;
    dialog.style.cssText = `
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        background: white;
        border: 2px solid #333;
        border-radius: 12px;
        padding: 20px;
        z-index: 100000;
        box-shadow: 0 4px 20px rgba(0,0,0,0.3);
        min-width: 280px;
        font-family: Arial, sans-serif;
    `;

    // Valores actuales
    const titleSize = chart.options.plugins.title?.font?.size || 22;
    const titleAlign = chart.options.plugins.title?.align || 'start';
    const labelSize = chart.options.plugins.datalabels?.font?.size || 14;
    const titleDisplay = chart.options.plugins.title?.display !== false;
    const legendDisplay = chart.options.plugins.legend?.display !== false;

    dialog.innerHTML = `
        <div style="font-weight: bold; margin-bottom: 15px; font-size: 16px; color: #3b4fa8;">⚙️ Configurar Gráfico</div>
        
        <div style="margin-bottom: 12px;">
            <label style="font-size: 12px; color: #555;">Título:</label><br>
            <button class="dialog-config-btn ${!titleDisplay ? 'activo' : ''}" id="title-off-${canvasId}">Off</button>
            <button class="dialog-config-btn ${titleDisplay ? 'activo' : ''}" id="title-on-${canvasId}">On</button>
        </div>
        
        <div style="margin-bottom: 12px;">
            <label style="font-size: 12px; color: #555;">Alineación título:</label><br>
            <button class="dialog-config-btn ${titleAlign === 'start' ? 'activo' : ''}" id="align-start-${canvasId}">← Izq</button>
            <button class="dialog-config-btn ${titleAlign === 'center' ? 'activo' : ''}" id="align-center-${canvasId}">↔ Cen</button>
            <button class="dialog-config-btn ${titleAlign === 'end' ? 'activo' : ''}" id="align-end-${canvasId}">→ Der</button>
        </div>
        
        <div style="margin-bottom: 12px;">
            <label style="font-size: 12px; color: #555;">Tamaño título: <span id="title-size-val-${canvasId}">${titleSize}</span>px</label><br>
            <button class="dialog-config-btn" id="title-down-${canvasId}">−</button>
            <button class="dialog-config-btn" id="title-up-${canvasId}">+</button>
        </div>
        
        <div style="margin-bottom: 12px;">
            <label style="font-size: 12px; color: #555;">Tamaño etiquetas: <span id="label-size-val-${canvasId}">${labelSize}</span>px</label><br>
            <button class="dialog-config-btn" id="label-down-${canvasId}">−</button>
            <button class="dialog-config-btn" id="label-up-${canvasId}">+</button>
        </div>
        
        <div style="margin-bottom: 12px;">
            <label style="font-size: 12px; color: #555;">Leyenda:</label><br>
            <button class="dialog-config-btn ${!legendDisplay ? 'activo' : ''}" id="legend-off-${canvasId}">Ocultar</button>
            <button class="dialog-config-btn ${legendDisplay ? 'activo' : ''}" id="legend-on-${canvasId}">Mostrar</button>
        </div>
        
        <hr style="margin: 12px 0; border-color: #e0e0e0;">
        <div style="display: flex; gap: 8px; justify-content: flex-end;">
            <button class="dialog-config-btn" id="close-dialog-${canvasId}" style="background: #f0f0f0; color: #666;">Cerrar</button>
            <button class="dialog-config-btn danger" id="reset-${canvasId}">Resetear</button>
        </div>
    `;

    document.body.appendChild(dialog);

    // Eventos
    document.getElementById(`title-off-${canvasId}`).onclick = () => {
        chart.options.plugins.title.display = false;
        chart.update();
        cerrarDialog();
    };

    document.getElementById(`title-on-${canvasId}`).onclick = () => {
        chart.options.plugins.title.display = true;
        chart.update();
        cerrarDialog();
    };

    document.getElementById(`align-start-${canvasId}`).onclick = () => {
        chart.options.plugins.title.align = 'start';
        chart.update();
        cerrarDialog();
    };

    document.getElementById(`align-center-${canvasId}`).onclick = () => {
        chart.options.plugins.title.align = 'center';
        chart.update();
        cerrarDialog();
    };

    document.getElementById(`align-end-${canvasId}`).onclick = () => {
        chart.options.plugins.title.align = 'end';
        chart.update();
        cerrarDialog();
    };

    document.getElementById(`title-up-${canvasId}`).onclick = () => {
        let current = chart.options.plugins.title.font?.size || 22;
        chart.options.plugins.title.font = { ...chart.options.plugins.title.font, size: current + 2 };
        document.getElementById(`title-size-val-${canvasId}`).innerText = current + 2;
        chart.update();
    };

    document.getElementById(`title-down-${canvasId}`).onclick = () => {
        let current = chart.options.plugins.title.font?.size || 22;
        chart.options.plugins.title.font = { ...chart.options.plugins.title.font, size: Math.max(10, current - 2) };
        document.getElementById(`title-size-val-${canvasId}`).innerText = Math.max(10, current - 2);
        chart.update();
    };

    document.getElementById(`label-up-${canvasId}`).onclick = () => {
        let current = chart.options.plugins.datalabels?.font?.size || 14;
        if (!chart.options.plugins.datalabels) chart.options.plugins.datalabels = {};
        if (!chart.options.plugins.datalabels.font) chart.options.plugins.datalabels.font = {};
        chart.options.plugins.datalabels.font.size = current + 2;
        document.getElementById(`label-size-val-${canvasId}`).innerText = current + 2;
        chart.update();
    };

    document.getElementById(`label-down-${canvasId}`).onclick = () => {
        let current = chart.options.plugins.datalabels?.font?.size || 14;
        if (!chart.options.plugins.datalabels) chart.options.plugins.datalabels = {};
        if (!chart.options.plugins.datalabels.font) chart.options.plugins.datalabels.font = {};
        chart.options.plugins.datalabels.font.size = Math.max(8, current - 2);
        document.getElementById(`label-size-val-${canvasId}`).innerText = Math.max(8, current - 2);
        chart.update();
    };

    document.getElementById(`legend-off-${canvasId}`).onclick = () => {
        chart.options.plugins.legend.display = false;
        chart.update();
        cerrarDialog();
    };

    document.getElementById(`legend-on-${canvasId}`).onclick = () => {
        chart.options.plugins.legend.display = true;
        chart.update();
        cerrarDialog();
    };

    document.getElementById(`reset-${canvasId}`).onclick = () => {
        location.reload();
    };

    function cerrarDialog() {
        dialog.remove();
    }

    document.getElementById(`close-dialog-${canvasId}`).onclick = cerrarDialog;

    // Cerrar al hacer clic fuera
    dialog.addEventListener('click', (e) => {
        if (e.target === dialog) cerrarDialog();
    });
}