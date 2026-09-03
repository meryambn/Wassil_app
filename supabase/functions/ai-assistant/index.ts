// Supabase Edge Function: ai-assistant
// Conversational AI assistant endpoint for the WASSIL Algerian delivery platform

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const { message, role, user_name } = await req.json();

    if (!message) {
      return new Response(
        JSON.stringify({ error: "Message parameter is required" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const geminiKey = Deno.env.get("GEMINI_API_KEY");
    const openaiKey = Deno.env.get("OPENAI_API_KEY");

    // System prompt encoding WASSIL platform knowledge
    const systemPrompt = `Tu es l'assistant virtuel intelligent officiel de la plateforme de livraison WASSIL en Algérie.
Ton rôle est d'accueillir et de renseigner les utilisateurs (expéditeurs et livreurs) en français avec un ton chaleureux, professionnel et adapté au contexte algérien (compréhension du Darija bienvenue).

RÈGLES IMPORTANTES :
1. PRIX : Ne donne jamais de calcul de prix direct ou inventé ; indique toujours que l'application calcule automatiquement le tarif via l'algorithme WASSIL basé sur la distance, le poids volumétrique et le type de colis.
2. SUIVI : Pour le suivi de commande, invite l'utilisateur à consulter sa carte en direct sur l'application.
3. KYC : Rappelle que les livreurs doivent soumettre leur Carte Nationale d'Identité (CIN) et leur Permis de conduire pour validation par l'administration.
4. RETRAITS : Les livreurs peuvent retirer leurs gains depuis leur portefeuille WASSIL.
5. SÉCURITÉ : Les matières dangereuses, armes, stupéfiants et produits chimiques toxiques sont formellement interdits.
6. Sois concis, clair et poli.`;

    if (geminiKey) {
      // Call Google Gemini API
      const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${geminiKey}`;
      const geminiRes = await fetch(geminiUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [
            {
              role: "user",
              parts: [
                { text: systemPrompt },
                { text: `L'utilisateur (${user_name || "Client"}, rôle: ${role || "sender"}) demande : "${message}"` }
              ]
            }
          ]
        })
      });

      if (geminiRes.ok) {
        const geminiData = await geminiRes.json();
        const replyText = geminiData.candidates?.[0]?.content?.parts?.[0]?.text;
        if (replyText) {
          return new Response(
            JSON.stringify({ reply: replyText }),
            { headers: { ...corsHeaders, "Content-Type": "application/json" } }
          );
        }
      }
    } else if (openaiKey) {
      // Call OpenAI API
      const openaiRes = await fetch("https://api.openai.com/v1/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${openaiKey}`
        },
        body: JSON.stringify({
          model: "gpt-4o-mini",
          messages: [
            { role: "system", content: systemPrompt },
            { role: "user", content: message }
          ],
          temperature: 0.7
        })
      });

      if (openaiRes.ok) {
        const openaiData = await openaiRes.json();
        const replyText = openaiData.choices?.[0]?.message?.content;
        if (replyText) {
          return new Response(
            JSON.stringify({ reply: replyText }),
            { headers: { ...corsHeaders, "Content-Type": "application/json" } }
          );
        }
      }
    }

    // Default intelligent knowledge response if no external API key is set
    const defaultReply = `Bonjour ! Je suis l'assistant virtuel WASSIL. Je suis là pour vous aider à suivre vos livraisons, estimer vos tarifs selon les wilayas d'Algérie, ou vous renseigner sur la validation KYC et les retraits de solde.`;

    return new Response(
      JSON.stringify({ reply: defaultReply }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
