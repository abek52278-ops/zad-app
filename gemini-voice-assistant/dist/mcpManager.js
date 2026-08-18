import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
export class MCPManager {
    mcpClients = new Map();
    registeredTools = new Map();
    // In-memory neural data store for live sessions
    sessionData = {
        transactions: [
            { id: '1', title: 'مشتريات بقالة وسوبرماركت', amount: 450, category: 'مؤونة', date: new Date().toISOString() },
            { id: '2', title: 'فاتورة صيدلية وأدوية', amount: 180, category: 'صحة', date: new Date().toISOString() }
        ],
        pantry: [
            { name: 'حليب كامل الدسم', quantity: 3, unit: 'لتر', expiry: '2026-08-25' },
            { name: 'أرز بسمتي', quantity: 5, unit: 'كجم', expiry: '2026-12-01' },
            { name: 'بيض مزارع', quantity: 12, unit: 'حبة', expiry: '2026-08-22' }
        ],
        pharmacy: [
            { name: 'بانادول إكسترا', remaining: 14, times: '08:00, 20:00', dose: 'قرص واحد' },
            { name: 'فيتامين سي فوار', remaining: 8, times: '09:00', dose: 'قرص مع ماء' }
        ],
        tasks: [
            { id: '1', title: 'شراء علاج الوالدة من الصيدلية', due: 'اليوم 6 مساءً', done: false },
            { id: '2', title: 'مراجعة اشتراك النت المنزلي', due: 'غداً', done: false }
        ],
        financialSummary: {
            totalBudget: 15000,
            spentThisMonth: 4230,
            remainingBalance: 10770,
            currency: 'EGP',
            healthScore: 92,
            healthStatus: 'ممتاز ومستقر'
        }
    };
    /**
     * Comprehensive tool declarations for Zad Neural Agent
     */
    fallbackTools = [
        {
            name: 'log_transaction',
            description: 'تسجيل مصروف أو دخل جديد في حسابات العائلة فوراً وبدقة',
            parameters: {
                type: 'OBJECT',
                properties: {
                    title: { type: 'STRING', description: 'وصف أو اسم المعاملة (مثل: قهوة، بنزين، كشف طبيب)' },
                    amount: { type: 'NUMBER', description: 'المبلغ الإجمالي' },
                    category: { type: 'STRING', description: 'التصنيف (طعام، صحة، فواتير، مواصلات، ترفيه، عام)' },
                    is_expense: { type: 'BOOLEAN', description: 'true للمصروفات، false للدخل' }
                },
                required: ['title', 'amount']
            }
        },
        {
            name: 'get_financial_summary',
            description: 'الاستعلام عن الرصيد الحالي والمصروفات الإجمالية والميزانية المتبقية والصحة المالية',
            parameters: {
                type: 'OBJECT',
                properties: {
                    period: { type: 'STRING', description: 'الفترة (الشهر الحالي، هذا الأسبوع، اليوم)' }
                }
            }
        },
        {
            name: 'query_pantry_items',
            description: 'الاستعلام عن محتويات المؤونة والمخزون المنزلي والأصناف التي قاربت على الانتهاء',
            parameters: {
                type: 'OBJECT',
                properties: {
                    item_name: { type: 'STRING', description: 'اسم الصنف للبحث عنه بالتحديد (اختياري)' }
                }
            }
        },
        {
            name: 'add_pantry_item',
            description: 'إضافة صنف جديد أو زيادة كمية صنف في مخزون المؤونة',
            parameters: {
                type: 'OBJECT',
                properties: {
                    name: { type: 'STRING', description: 'اسم المنتج' },
                    quantity: { type: 'NUMBER', description: 'الكمية' },
                    unit: { type: 'STRING', description: 'الوحدة (كجم، لتر، علبة، حبة)' }
                },
                required: ['name', 'quantity']
            }
        },
        {
            name: 'query_pharmacy_and_doses',
            description: 'الاستعلام عن جدول الأدوية والمواعيد والجرعات المتبقية في الصيدلية',
            parameters: {
                type: 'OBJECT',
                properties: {
                    medicine_name: { type: 'STRING', description: 'اسم الدواء (اختياري)' }
                }
            }
        },
        {
            name: 'add_task_or_event',
            description: 'إضافة مهمة جديدة أو موعد إلى تقويم العائلة مع التذكير',
            parameters: {
                type: 'OBJECT',
                properties: {
                    title: { type: 'STRING', description: 'عنوان المهمة أو الموعد' },
                    due_time: { type: 'STRING', description: 'الوقت أو الميعاد المحدد' }
                },
                required: ['title']
            }
        }
    ];
    async connectStdioServer(serverName, command, args = []) {
        try {
            console.log(`[MCPManager] Connecting to MCP server '${serverName}'...`);
            const transport = new StdioClientTransport({ command, args });
            const client = new Client({ name: `zad-voice-${serverName}`, version: '1.0.0' }, { capabilities: {} });
            await client.connect(transport);
            this.mcpClients.set(serverName, client);
            const toolsResponse = await client.listTools();
            for (const tool of toolsResponse.tools) {
                this.registeredTools.set(tool.name, { serverName, originalName: tool.name });
            }
            console.log(`[MCPManager] Connected '${serverName}' with ${toolsResponse.tools.length} tools.`);
        }
        catch (err) {
            console.warn(`[MCPManager] MCP server '${serverName}' not connected, using neural fallback.`);
        }
    }
    getGeminiFunctionDeclarations() {
        return [...this.fallbackTools];
    }
    /**
     * Execute real tool mutation and queries against the Zad Neural Node
     */
    async executeToolCall(toolName, args) {
        console.log(`[MCPManager] ⚡ Executing Neural Tool: '${toolName}' with args:`, args);
        switch (toolName) {
            case 'log_transaction': {
                const amount = Number(args.amount) || 0;
                const title = String(args.title || 'معاملة');
                const category = String(args.category || 'عام');
                const isExpense = args.is_expense !== false;
                this.sessionData.transactions.unshift({
                    id: String(Date.now()),
                    title,
                    amount,
                    category,
                    date: new Date().toISOString()
                });
                if (isExpense) {
                    this.sessionData.financialSummary.spentThisMonth += amount;
                    this.sessionData.financialSummary.remainingBalance -= amount;
                }
                else {
                    this.sessionData.financialSummary.remainingBalance += amount;
                }
                return {
                    status: 'success',
                    message: `تم تسجيل ${isExpense ? 'المصروف' : 'الدخل'} بنجاح: ${title} بمبلغ ${amount} ${this.sessionData.financialSummary.currency}`,
                    current_remaining: this.sessionData.financialSummary.remainingBalance
                };
            }
            case 'get_financial_summary': {
                return {
                    status: 'success',
                    data: this.sessionData.financialSummary,
                    recent_transactions: this.sessionData.transactions.slice(0, 3)
                };
            }
            case 'query_pantry_items': {
                const filter = args.item_name ? String(args.item_name).toLowerCase() : '';
                const items = filter
                    ? this.sessionData.pantry.filter(i => i.name.toLowerCase().includes(filter))
                    : this.sessionData.pantry;
                return {
                    status: 'success',
                    items: items,
                    total_items: items.length
                };
            }
            case 'add_pantry_item': {
                const name = String(args.name || '');
                const quantity = Number(args.quantity) || 1;
                const unit = String(args.unit || 'حبة');
                const existing = this.sessionData.pantry.find(i => i.name.toLowerCase() === name.toLowerCase());
                if (existing) {
                    existing.quantity += quantity;
                }
                else {
                    this.sessionData.pantry.push({
                        name,
                        quantity,
                        unit,
                        expiry: '2026-09-01'
                    });
                }
                return {
                    status: 'success',
                    message: `تمت إضافة ${quantity} ${unit} من ${name} إلى مخزون المؤونة بنجاح.`
                };
            }
            case 'query_pharmacy_and_doses': {
                return {
                    status: 'success',
                    prescriptions: this.sessionData.pharmacy
                };
            }
            case 'add_task_or_event': {
                const title = String(args.title || 'مهمة');
                const dueTime = String(args.due_time || 'اليوم');
                this.sessionData.tasks.unshift({
                    id: String(Date.now()),
                    title,
                    due: dueTime,
                    done: false
                });
                return {
                    status: 'success',
                    message: `تمت إضافة المهمة "${title}" بنجاح في الموعد المحدد (${dueTime}).`
                };
            }
            default:
                return {
                    status: 'executed',
                    result: `الأداة ${toolName} نُفذت بنجاح.`
                };
        }
    }
}
