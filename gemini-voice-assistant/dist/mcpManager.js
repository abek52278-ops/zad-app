import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
export class MCPManager {
    mcpClients = new Map();
    registeredTools = new Map();
    /**
     * Register standard sample tools for instant out-of-the-box functionality
     */
    fallbackTools = [
        {
            name: 'get_calendar_events',
            description: 'استرجاع قائمة المواعيد والأحداث المسجلة في التقويم ليوم محدد',
            parameters: {
                type: 'OBJECT',
                properties: {
                    date: { type: 'STRING', description: 'التاريخ بصيغة YYYY-MM-DD أو "today" / "tomorrow"' }
                },
                required: ['date']
            }
        },
        {
            name: 'add_task',
            description: 'إضافة مهمة جديدة أو تذكير إلى قائمة المهام',
            parameters: {
                type: 'OBJECT',
                properties: {
                    title: { type: 'STRING', description: 'عنوان المهمة' },
                    category: { type: 'STRING', description: 'تصنيف المهمة (شخصي، منزل، عمل، زاد)' },
                    due_date: { type: 'STRING', description: 'تاريخ الاستحقاق إن وجد' }
                },
                required: ['title']
            }
        },
        {
            name: 'query_pantry_items',
            description: 'الاستعلام عن المنتجات المتوفرة في المؤونة أو النواقص',
            parameters: {
                type: 'OBJECT',
                properties: {
                    category: { type: 'STRING', description: 'الفئة (طعام، صيدلية، منظفات، الكل)' }
                }
            }
        }
    ];
    /**
     * Connect to an external MCP server over Stdio transport (e.g. mcp-server-sqlite, mcp-server-google-calendar)
     */
    async connectStdioServer(serverName, command, args = []) {
        try {
            console.log(`[MCPManager] Connecting to MCP server '${serverName}' via command: ${command} ${args.join(' ')}...`);
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
            console.warn(`[MCPManager] Failed to connect MCP server '${serverName}'. Using fallback tools.`, err);
        }
    }
    /**
     * Generate Gemini Function Declarations for all MCP & fallback tools
     */
    getGeminiFunctionDeclarations() {
        const declarations = [...this.fallbackTools];
        // Convert external MCP tool schemas to Gemini format
        for (const [toolName, info] of this.registeredTools.entries()) {
            declarations.push({
                name: toolName,
                description: `MCP Tool: ${toolName} from ${info.serverName}`,
                parameters: {
                    type: 'OBJECT',
                    properties: {},
                    required: []
                }
            });
        }
        return declarations;
    }
    /**
     * Execute tool call coming from Gemini Live API
     */
    async executeToolCall(name, args) {
        console.log(`[MCPManager] Executing Tool Call: '${name}' with args:`, JSON.stringify(args));
        // Check if tool belongs to connected MCP server
        const registered = this.registeredTools.get(name);
        if (registered) {
            const client = this.mcpClients.get(registered.serverName);
            if (client) {
                try {
                    const result = await client.callTool({ name: registered.originalName, arguments: args });
                    return { success: true, result };
                }
                catch (err) {
                    return { success: false, error: err.message };
                }
            }
        }
        // Handle Fallback Standard Tools (Deterministic Mock/Execution)
        if (name === 'get_calendar_events') {
            const date = args.date || 'today';
            return {
                success: true,
                events: [
                    { time: '10:00 AM', title: 'اجتماع فريق العمل' },
                    { time: '04:30 PM', title: 'شراء نواقص البيت والمؤونة (زاد)' }
                ],
                queryDate: date
            };
        }
        if (name === 'add_task') {
            return {
                success: true,
                message: `تم إضافة المهمة "${args.title}" بنجاح إلى قائمة ${args.category || 'المجموعات'}.`,
                taskId: `task_${Date.now()}`
            };
        }
        if (name === 'query_pantry_items') {
            return {
                success: true,
                items: [
                    { name: 'حليب', quantity: '2 لتر', status: 'كافي' },
                    { name: 'أرز بستمي', quantity: '1 كجم', status: 'ينتهي قريباً' },
                    { name: 'زيت زيتون', quantity: '0.5 لتر', status: 'ينقص' }
                ]
            };
        }
        return { success: false, error: `Unknown tool '${name}'` };
    }
}
